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
  - run.sh, a one-click startup script
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
exec java -jar "${APP_NAME}.jar" "\$@"
RUNEOF
chmod +x "${STAGING_DIR}/run.sh"

rm -f "${DIST_ARCHIVE_PATH}" "${TARGET_ARCHIVE_PATH}"
tar -C "${DIST_ROOT}" -czf "${DIST_ARCHIVE_PATH}" "${APP_NAME}"
cp "${DIST_ARCHIVE_PATH}" "${TARGET_ARCHIVE_PATH}"

echo "==> Archive created: ${TARGET_ARCHIVE_PATH}"
echo "==> Archive also copied to: ${DIST_ARCHIVE_PATH}"
echo "==> Included external config: ${APP_NAME}/config/application.yml"
echo "==> Included route config directory: ${APP_NAME}/config/routes"
