#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
cd "${PROJECT_ROOT}"

RUN_TESTS=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --with-tests)
      RUN_TESTS=true
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/build-dist.sh [--with-tests]

Compile the Maven project, package the Spring Boot jar, and create tar.gz
archives at both:
  - target/web-router-<version>.tar.gz
  - target/dist/web-router-<version>.tar.gz

Tests are skipped by default so the script can be used as a packaging command;
pass --with-tests to run the full Maven test phase.

The release archive contains:
  - the executable Spring Boot jar
  - run.sh / run.bat, one-click startup scripts
  - stop.sh / stop.bat, one-click stop scripts
  - config/application.yml, the external backend configuration file
  - config/routes/, including existing route JSON files when present
  - README/USAGE/CHANGELOG docs when present

Options:
  --with-tests   Run tests during Maven package
  -h, --help     Show this help
USAGE
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Run scripts/build-dist.sh --help for usage." >&2
      exit 2
      ;;
  esac
done

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn is required but was not found in PATH." >&2
  exit 1
fi

copy_route_configs() {
  source_dir=$1
  target_dir=$2

  mkdir -p "${target_dir}"
  if [ ! -d "${source_dir}" ]; then
    return 0
  fi

  find "${source_dir}" -maxdepth 1 -type f -name '*.json' -exec cp {} "${target_dir}/" \;
}

convert_to_crlf() {
  file_path=$1
  tmp_path="${file_path}.tmp"

  awk '{ printf "%s\r\n", $0 }' "${file_path}" > "${tmp_path}"
  mv "${tmp_path}" "${file_path}"
}

if [ "${RUN_TESTS}" = "true" ]; then
  echo "==> Building project: mvn clean package"
  mvn clean package
else
  echo "==> Building project: mvn clean package -DskipTests"
  mvn clean package -DskipTests
fi

ARTIFACT_ID=$(mvn help:evaluate -Dexpression=project.artifactId -q -DforceStdout)
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
APP_NAME="${ARTIFACT_ID}-${VERSION}"
DIST_ROOT="${PROJECT_ROOT}/target/dist"
STAGING_DIR="${DIST_ROOT}/${APP_NAME}"
DIST_ARCHIVE_PATH="${DIST_ROOT}/${APP_NAME}.tar.gz"
TARGET_ARCHIVE_PATH="${PROJECT_ROOT}/target/${APP_NAME}.tar.gz"
JAR_PATH="${PROJECT_ROOT}/target/${APP_NAME}.jar"
APPLICATION_CONFIG="${PROJECT_ROOT}/src/main/resources/application.yml"
ROUTES_CONFIG_DIR="${PROJECT_ROOT}/config/routes"

if [ ! -f "${JAR_PATH}" ]; then
  echo "Expected jar was not found: ${JAR_PATH}" >&2
  exit 1
fi

if [ ! -f "${APPLICATION_CONFIG}" ]; then
  echo "Expected backend config was not found: ${APPLICATION_CONFIG}" >&2
  exit 1
fi

rm -rf "${STAGING_DIR}"
mkdir -p "${STAGING_DIR}/config/routes"

cp "${JAR_PATH}" "${STAGING_DIR}/"
cp "${APPLICATION_CONFIG}" "${STAGING_DIR}/config/application.yml"
copy_route_configs "${ROUTES_CONFIG_DIR}" "${STAGING_DIR}/config/routes"

for doc in README.md USAGE.md CHANGELOG.md; do
  if [ -f "${doc}" ]; then
    cp "${doc}" "${STAGING_DIR}/"
  fi
done

cat > "${STAGING_DIR}/run.sh" <<RUNEOF
#!/bin/sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
cd "\${APP_DIR}"

JAR_FILE="\${APP_DIR}/${APP_NAME}.jar"
PID_FILE="\${APP_DIR}/web-router.pid"
LOG_DIR="\${APP_DIR}/logs"
LOG_OUT_FILE="\${LOG_DIR}/web-router.out"
LOG_ERR_FILE="\${LOG_DIR}/web-router.err"
BOOTSTRAP_OUT_FILE="\${LOG_DIR}/web-router.bootstrap.out"
BOOTSTRAP_ERR_FILE="\${LOG_DIR}/web-router.bootstrap.err"
START_WAIT_SECONDS="\${WEB_ROUTER_START_WAIT_SECONDS:-5}"

pid_matches_app() {
  pid=\$1
  command=\$(ps -p "\${pid}" -o command= 2>/dev/null || true)
  case "\${command}" in
    "") return 0 ;;
    *"\${JAR_FILE}"*) return 0 ;;
    *) return 1 ;;
  esac
}

if [ ! -f "\${JAR_FILE}" ]; then
  echo "Jar file not found: \${JAR_FILE}" >&2
  exit 1
fi

if [ -f "\${PID_FILE}" ]; then
  OLD_PID=\$(cat "\${PID_FILE}" 2>/dev/null || true)
  if [ -n "\${OLD_PID}" ] && kill -0 "\${OLD_PID}" 2>/dev/null && pid_matches_app "\${OLD_PID}"; then
    echo "web-router is already running, pid=\${OLD_PID}"
    exit 0
  fi
  rm -f "\${PID_FILE}"
fi

mkdir -p "\${LOG_DIR}" "\${APP_DIR}/config/routes"
nohup java -jar "\${JAR_FILE}" "\$@" > "\${BOOTSTRAP_OUT_FILE}" 2> "\${BOOTSTRAP_ERR_FILE}" &
APP_PID=\$!
echo "\${APP_PID}" > "\${PID_FILE}"

sleep "\${START_WAIT_SECONDS}"
if ! kill -0 "\${APP_PID}" 2>/dev/null; then
  rm -f "\${PID_FILE}"
  echo "web-router failed to start. Recent log output:" >&2
  tail -n 80 "\${LOG_ERR_FILE}" >&2 || true
  tail -n 80 "\${LOG_OUT_FILE}" >&2 || true
  tail -n 80 "\${BOOTSTRAP_ERR_FILE}" >&2 || true
  tail -n 80 "\${BOOTSTRAP_OUT_FILE}" >&2 || true
  exit 1
fi

echo "web-router started, pid=\${APP_PID}"
echo "Log file: \${LOG_OUT_FILE}"
echo "Error log file: \${LOG_ERR_FILE}"
RUNEOF
chmod +x "${STAGING_DIR}/run.sh"

cat > "${STAGING_DIR}/stop.sh" <<STOPEOF
#!/bin/sh
set -eu
APP_DIR=\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)
cd "\${APP_DIR}"

APP_NAME="${APP_NAME}"
JAR_FILE="\${APP_DIR}/\${APP_NAME}.jar"
PID_FILE="\${APP_DIR}/web-router.pid"

is_running() {
  pid=\$1
  [ -n "\${pid}" ] && kill -0 "\${pid}" 2>/dev/null
}

pid_matches_app() {
  pid=\$1
  command=\$(ps -p "\${pid}" -o command= 2>/dev/null || true)
  case "\${command}" in
    "") return 0 ;;
    *"\${JAR_FILE}"*) return 0 ;;
    *) return 1 ;;
  esac
}

stop_pid() {
  pid=\$1

  if ! is_running "\${pid}"; then
    return 0
  fi

  echo "Stopping web-router, pid=\${pid}"
  kill "\${pid}" 2>/dev/null || true

  count=0
  while is_running "\${pid}"; do
    count=\$((count + 1))
    if [ "\${count}" -ge 30 ]; then
      echo "Process did not stop within 30 seconds, force killing pid=\${pid}"
      kill -9 "\${pid}" 2>/dev/null || true
      break
    fi
    sleep 1
  done
}

STOPPED=false

if [ -f "\${PID_FILE}" ]; then
  PID=\$(cat "\${PID_FILE}" 2>/dev/null || true)
  if is_running "\${PID}" && pid_matches_app "\${PID}"; then
    stop_pid "\${PID}"
    STOPPED=true
  elif is_running "\${PID}"; then
    echo "PID file points to a different process, not killing pid=\${PID}" >&2
  fi
  rm -f "\${PID_FILE}"
fi

PIDS=\$(ps -eo pid=,command= 2>/dev/null | awk -v jar="\${JAR_FILE}" 'index(\$0, jar) { print \$1 }')
for PID in \${PIDS}; do
  if is_running "\${PID}"; then
    stop_pid "\${PID}"
    STOPPED=true
  fi
done

rm -f "\${PID_FILE}"

if [ "\${STOPPED}" = "true" ]; then
  echo "web-router stopped"
else
  echo "web-router is not running"
fi
STOPEOF
chmod +x "${STAGING_DIR}/stop.sh"

cat > "${STAGING_DIR}/run.bat" <<RUNBATEOF
@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "APP_NAME=${APP_NAME}"
set "APP_DIR=%CD%"
set "JAR_FILE=%CD%\\%APP_NAME%.jar"
set "PID_FILE=%CD%\\web-router.pid"
set "LOG_DIR=%CD%\\logs"
set "LOG_OUT_FILE=%LOG_DIR%\\web-router.out"
set "LOG_ERR_FILE=%LOG_DIR%\\web-router.err"
set "BOOTSTRAP_OUT_FILE=%LOG_DIR%\\web-router.bootstrap.out"
set "BOOTSTRAP_ERR_FILE=%LOG_DIR%\\web-router.bootstrap.err"
set "APP_ARGS=%*"
if "%WEB_ROUTER_START_WAIT_SECONDS%"=="" set "WEB_ROUTER_START_WAIT_SECONDS=5"

if not exist "%JAR_FILE%" (
  echo Jar file not found: "%JAR_FILE%"
  exit /b 1
)

if exist "%PID_FILE%" (
  set /p OLD_PID=<"%PID_FILE%"
  if not "!OLD_PID!"=="" (
    set "APP_PID=!OLD_PID!"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "\$pidValue = [int]\$env:APP_PID; \$jar = \$env:JAR_FILE; \$process = Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -eq \$pidValue }; if (\$process -and ((-not \$process.CommandLine) -or (\$process.CommandLine -like ('*' + \$jar + '*')))) { exit 0 } else { exit 1 }" >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
      echo web-router is already running, pid=!OLD_PID!
      exit /b 0
    )
  )
  del /f /q "%PID_FILE%" >nul 2>nul
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%CD%\\config\\routes" mkdir "%CD%\\config\\routes"

powershell -NoProfile -ExecutionPolicy Bypass -Command "\$jar = \$env:JAR_FILE; \$appArgs = \$env:APP_ARGS; \$quote = [char]34; \$argLine = '-jar ' + \$quote + \$jar + \$quote; if (\$appArgs) { \$argLine = \$argLine + ' ' + \$appArgs }; \$process = Start-Process -FilePath 'java' -ArgumentList \$argLine -WorkingDirectory \$env:APP_DIR -RedirectStandardOutput \$env:BOOTSTRAP_OUT_FILE -RedirectStandardError \$env:BOOTSTRAP_ERR_FILE -WindowStyle Hidden -PassThru; \$process.Id" > "%PID_FILE%"

if errorlevel 1 (
  del /f /q "%PID_FILE%" >nul 2>nul
  echo web-router failed to start.
  exit /b 1
)

set /p APP_PID=<"%PID_FILE%"
timeout /t %WEB_ROUTER_START_WAIT_SECONDS% /nobreak >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-Process -Id %APP_PID% -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
if errorlevel 1 (
  del /f /q "%PID_FILE%" >nul 2>nul
  echo web-router failed to start. Recent log output:
  powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path \$env:LOG_ERR_FILE) { Get-Content \$env:LOG_ERR_FILE -Tail 80 }; if (Test-Path \$env:LOG_OUT_FILE) { Get-Content \$env:LOG_OUT_FILE -Tail 80 }; if (Test-Path \$env:BOOTSTRAP_ERR_FILE) { Get-Content \$env:BOOTSTRAP_ERR_FILE -Tail 80 }; if (Test-Path \$env:BOOTSTRAP_OUT_FILE) { Get-Content \$env:BOOTSTRAP_OUT_FILE -Tail 80 }"
  exit /b 1
)

echo web-router started, pid=%APP_PID%
echo Log file: %LOG_OUT_FILE%
echo Error log file: %LOG_ERR_FILE%
RUNBATEOF

cat > "${STAGING_DIR}/stop.bat" <<STOPBATEOF
@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "APP_NAME=${APP_NAME}"
set "JAR_FILE=%CD%\\%APP_NAME%.jar"
set "PID_FILE=%CD%\\web-router.pid"
set "STOPPED=false"

if exist "%PID_FILE%" (
  set /p APP_PID=<"%PID_FILE%"
  if not "!APP_PID!"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "\$pidValue = [int]\$env:APP_PID; \$jar = \$env:JAR_FILE; \$process = Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -eq \$pidValue }; if (\$process -and ((-not \$process.CommandLine) -or (\$process.CommandLine -like ('*' + \$jar + '*')))) { Stop-Process -Id \$pidValue -Force; exit 0 } else { exit 1 }" >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
      echo web-router stopped, pid=!APP_PID!
      set "STOPPED=true"
    )
  )
  del /f /q "%PID_FILE%" >nul 2>nul
)

if "%STOPPED%"=="false" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "\$jar = '%JAR_FILE%'; \$processes = Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -like ('*' + \$jar + '*') }; if (\$processes) { \$processes | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }; exit 0 } else { exit 1 }" >nul 2>nul
  if !ERRORLEVEL! EQU 0 (
    echo web-router stopped
    set "STOPPED=true"
  )
)

if "%STOPPED%"=="false" (
  echo web-router is not running
)
STOPBATEOF

convert_to_crlf "${STAGING_DIR}/run.bat"
convert_to_crlf "${STAGING_DIR}/stop.bat"

rm -f "${DIST_ARCHIVE_PATH}" "${TARGET_ARCHIVE_PATH}"
tar -C "${DIST_ROOT}" -czf "${DIST_ARCHIVE_PATH}" "${APP_NAME}"
cp "${DIST_ARCHIVE_PATH}" "${TARGET_ARCHIVE_PATH}"

echo "==> Archive created: ${TARGET_ARCHIVE_PATH}"
echo "==> Archive also copied to: ${DIST_ARCHIVE_PATH}"
echo "==> Included external config: ${APP_NAME}/config/application.yml"
echo "==> Included route config directory: ${APP_NAME}/config/routes"
