#!/usr/bin/env bash
# ============================================================================
# wrouter cross-platform packaging entry point (run on macOS / Linux)
#
#   scripts/build-package.sh --type <jar|app-image|installer>
#                            [--platform <macos-arm64|macos-x64|linux-x64|windows-x64>]
#                            [--skip-tests]
#
# Contract asset names (see docs/PACKAGING.md):
#   jar        wrouter-<version>-jar.zip          (+ .tar.gz)
#   app-image  wrouter-<version>-<platform>-app.<zip|tar.gz>
#   installer  wrouter-<version>-<platform>.<dmg|deb|rpm|exe>
# ============================================================================

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
# shellcheck source=scripts/lib/common.sh
. "${SCRIPT_DIR}/lib/common.sh"
cd "${PROJECT_ROOT}"

# jpackage 的 --name 同时决定 app-image 目录名与可执行文件名。
# 自动更新脚本（UpdateScriptGenerator）按 wrouter.app / wrouter 定位可执行文件，
# 因此这里必须使用小写 wrouter；显示名通过 --description/Info.plist 体现。
APP_DISPLAY_NAME="wrouter"
MAIN_JAR_NAME="wrouter.jar"
# The Spring Boot fat jar keeps the application classes under BOOT-INF/classes,
# so com.geek.webrouter.Application is NOT on the plain classpath jpackage
# builds from --main-jar. Spring Boot's own launcher is at the jar root and is
# the manifest Main-Class, so it is what jpackage must be pointed at.
MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"
APP_MAIN_CLASS="com.geek.webrouter.Application"
DEFAULT_WIN_UPGRADE_UUID="8f3c1d2e-5b64-4a7f-9c31-2d0e6b7a4f10"

PACKAGE_TYPE=""
PLATFORM=""
RUN_TESTS=true
SKIP_BUILD=false

usage() {
  cat <<'USAGE'
Usage: scripts/build-package.sh --type <jar|app-image|installer> [options]

Options:
  --type <jar|app-image|installer>   Required. What to produce.
  --platform <name>                  One of: macos-arm64, macos-x64,
                                     linux-x64, linux-arm64, windows-x64.
                                     Defaults to the current host platform.
  --skip-tests                       Pass -DskipTests to Maven.
  --skip-build                       Reuse the existing target/wrouter-<version>.jar
                                     instead of running Maven again (used by
                                     scripts/build-release.sh after its first build).
  -h, --help                         Show this help.

Types:
  jar        Executable Spring Boot jar packaged by scripts/build-dist.sh.
             Produces wrouter-<version>-jar.zip and .tar.gz.
  app-image  Self-contained application directory with a bundled trimmed
             runtime (jpackage --type app-image), zipped/tarred.
  installer  Native installer: dmg (macOS), deb + rpm (Linux), exe (Windows).

jpackage cannot cross-build: an app-image or installer must be produced on a
runner matching --platform. CI does this in a matrix; see
.github/workflows/release.yml.

Environment:
  WROUTER_VERSION_OVERRIDE   Override the version read from the build.
  WROUTER_RELEASE_DIR        Write contract assets here instead of
                                target/release (used by build-release.sh).
  WROUTER_MAVEN_EXTRA_ARGS   Extra flags appended to the Maven command,
                                e.g. -Dmaven.test.skip=true when the test
                                sources do not compile.
  WROUTER_JPACKAGE_MODULES   Comma separated jlink module list used to trim
                                the bundled runtime. Unset bundles a full JDK
                                (~147 MB unpacked); see docs/PACKAGING.md.
  WROUTER_ALLOW_CROSS=1      Attempt a cross build instead of failing.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --type)
      [ "$#" -ge 2 ] || die "--type requires a value"
      PACKAGE_TYPE=$2
      shift 2
      ;;
    --type=*)
      PACKAGE_TYPE=${1#--type=}
      shift
      ;;
    --platform)
      [ "$#" -ge 2 ] || die "--platform requires a value"
      PLATFORM=$2
      shift 2
      ;;
    --platform=*)
      PLATFORM=${1#--platform=}
      shift
      ;;
    --skip-tests)
      RUN_TESTS=false
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      log_error "unknown option: $1"
      echo "Run scripts/build-package.sh --help for usage." >&2
      exit 2
      ;;
  esac
done

[ -n "${PACKAGE_TYPE}" ] || { usage >&2; die "--type is required"; }

case "${PACKAGE_TYPE}" in
  jar|app-image|installer) ;;
  *) die "unsupported --type '${PACKAGE_TYPE}' (expected jar, app-image or installer)" ;;
esac

if [ -z "${PLATFORM}" ]; then
  PLATFORM=$(detect_host_platform)
  log_info "no --platform given, using host platform: ${PLATFORM}"
fi

case "${PLATFORM}" in
  macos-arm64) OS_FAMILY=macos ;;
  macos-x64)   OS_FAMILY=macos ;;
  linux-x64)   OS_FAMILY=linux ;;
  linux-arm64) OS_FAMILY=linux ;;
  windows-x64) OS_FAMILY=windows ;;
  *) die "unsupported --platform '${PLATFORM}'" ;;
esac

require_cmd mvn
[ -f "${PROJECT_ROOT}/pom.xml" ] || die "pom.xml not found in ${PROJECT_ROOT}"

TARGET_DIR="${PROJECT_ROOT}/target"
# build-release.sh stages assets outside target/ because every "mvn clean"
# wipes target/ and would otherwise delete a previously built asset.
RELEASE_DIR="${WROUTER_RELEASE_DIR:-${TARGET_DIR}/release}"
case "${RELEASE_DIR}" in
  /*) ;;
  *) RELEASE_DIR="${PROJECT_ROOT}/${RELEASE_DIR}" ;;
esac
DIST_DIR="${TARGET_DIR}/dist"
JPACKAGE_WORK_DIR="${TARGET_DIR}/jpackage"
PACKAGE_INPUT_DIR="${TARGET_DIR}/package-input"
RESOURCE_DIR="${TARGET_DIR}/jpackage-resources"
BUILD_TIME=$(iso8601_now)

if [ "${RUN_TESTS}" = "true" ]; then
  MAVEN_TEST_FLAG=""
else
  MAVEN_TEST_FLAG="-DskipTests"
fi

# --------------------------------------------------------------- jar build --
log_step "Building executable jar"
log_info "platform=${PLATFORM} installMode=${PACKAGE_TYPE} buildTime=${BUILD_TIME}"
if [ -n "${MAVEN_TEST_FLAG}" ]; then
  log_info "mvn clean package -DskipTests"
else
  log_info "mvn clean package (tests enabled)"
fi

if [ "${SKIP_BUILD}" = "true" ]; then
  log_info "--skip-build: reusing the existing build output"
else
  # WROUTER_MAVEN_EXTRA_ARGS is an escape hatch for local packaging when the
  # test sources do not compile, e.g. -Dmaven.test.skip=true (which, unlike
  # -DskipTests, also skips test compilation).
  # shellcheck disable=SC2086
  mvn clean package ${MAVEN_TEST_FLAG} ${WROUTER_MAVEN_EXTRA_ARGS:-} \
    -Dwrouter.platform="${PLATFORM}" \
    -Dwrouter.installMode="${PACKAGE_TYPE}" \
    -Dwrouter.buildTime="${BUILD_TIME}" \
    -B
fi

VERSION=$(resolve_app_version)
[ -n "${VERSION}" ] || die "could not resolve the project version"
PKG_VERSION=$(sanitize_pkg_version "${VERSION}")
SPRING_JAR="${TARGET_DIR}/wrouter-${VERSION}.jar"
[ -f "${SPRING_JAR}" ] || die "expected Spring Boot jar not found: ${SPRING_JAR}"

log_info "version=${VERSION} packageVersion=${PKG_VERSION}"

mkdir -p "${RELEASE_DIR}"

# ================================================================== JAR MODE =
build_jar() {
  log_step "Packaging JAR distribution"
  # The jar was already built above with the correct build metadata, so reuse
  # it (--skip-build) instead of paying for a second Maven run.
  if [ "${RUN_TESTS}" = "true" ]; then
    "${SCRIPT_DIR}/build-dist.sh" --with-tests --skip-build --platform "${PLATFORM}" --install-mode jar
  else
    "${SCRIPT_DIR}/build-dist.sh" --skip-tests --skip-build --platform "${PLATFORM}" --install-mode jar
  fi

  contract_zip="${RELEASE_DIR}/wrouter-${VERSION}-jar.zip"
  contract_tgz="${RELEASE_DIR}/wrouter-${VERSION}-jar.tar.gz"

  if [ -f "${DIST_DIR}/wrouter-${VERSION}-jar.zip" ]; then
    cp "${DIST_DIR}/wrouter-${VERSION}-jar.zip" "${contract_zip}"
  elif [ -f "${DIST_DIR}/wrouter-${VERSION}.zip" ]; then
    cp "${DIST_DIR}/wrouter-${VERSION}.zip" "${contract_zip}"
  else
    die "build-dist.sh did not produce a zip archive under ${DIST_DIR}"
  fi

  if [ -f "${DIST_DIR}/wrouter-${VERSION}-jar.tar.gz" ]; then
    cp "${DIST_DIR}/wrouter-${VERSION}-jar.tar.gz" "${contract_tgz}"
  elif [ -f "${DIST_DIR}/wrouter-${VERSION}.tar.gz" ]; then
    cp "${DIST_DIR}/wrouter-${VERSION}.tar.gz" "${contract_tgz}"
  else
    log_warn "no tar.gz archive found under ${DIST_DIR}; only the zip will be released"
  fi

  log_step "JAR distribution ready"
  log_info "zip:    ${contract_zip}"
  if [ -f "${contract_tgz}" ]; then
    log_info "tar.gz: ${contract_tgz}"
  fi
  return 0
}

# ============================================================== ICON SUPPORT =
ICON_PATH=""
ICON_NOTE=""

prepare_icon() {
  favicon="${PROJECT_ROOT}/frontend/public/favicon.svg"
  if [ ! -f "${favicon}" ]; then
    ICON_NOTE="frontend/public/favicon.svg not found; packaging without an icon"
    return 0
  fi

  if ! detect_python; then
    ICON_NOTE="no python3/python with zlib available; packaging without an icon"
    return 0
  fi

  mkdir -p "${RESOURCE_DIR}"
  icon_png="${RESOURCE_DIR}/wrouter.png"

  if ! "${PYTHON_BIN}" "${SCRIPT_DIR}/lib/svg-to-png.py" "${favicon}" "${icon_png}" 512 >/dev/null 2>&1; then
    ICON_NOTE="could not rasterise favicon.svg; packaging without an icon"
    rm -f "${icon_png}"
    return 0
  fi

  case "${OS_FAMILY}" in
    macos)
      if command -v sips >/dev/null 2>&1 && command -v iconutil >/dev/null 2>&1; then
        iconset="${RESOURCE_DIR}/wrouter.iconset"
        rm -rf "${iconset}"
        mkdir -p "${iconset}"
        icons_ok=true
        for spec in "16 16x16" "32 16x16@2x" "32 32x32" "64 32x32@2x" "128 128x128" "256 128x128@2x" "256 256x256" "512 256x256@2x" "512 512x512" "1024 512x512@2x"; do
          px=${spec%% *}
          label=${spec##* }
          sips -z "${px}" "${px}" "${icon_png}" --out "${iconset}/icon_${label}.png" >/dev/null 2>&1 || icons_ok=false
        done
        if [ "${icons_ok}" = "true" ] && iconutil -c icns "${iconset}" -o "${RESOURCE_DIR}/wrouter.icns" >/dev/null 2>&1; then
          ICON_PATH="${RESOURCE_DIR}/wrouter.icns"
        else
          ICON_NOTE="sips/iconutil could not build an .icns; packaging without an icon"
        fi
        rm -rf "${iconset}"
      else
        ICON_NOTE="sips/iconutil unavailable; packaging without an icon"
      fi
      ;;
    windows)
      icon_ico="${RESOURCE_DIR}/wrouter.ico"
      if "${PYTHON_BIN}" "${SCRIPT_DIR}/lib/png-to-ico.py" "${icon_png}" "${icon_ico}" >/dev/null 2>&1; then
        ICON_PATH="${icon_ico}"
      else
        ICON_NOTE="could not build a .ico; packaging without an icon"
      fi
      ;;
    linux)
      ICON_PATH="${icon_png}"
      ;;
  esac

  if [ -n "${ICON_PATH}" ]; then
    log_info "icon: ${ICON_PATH} (generated from frontend/public/favicon.svg)"
  fi
  return 0
}

# ============================================================ JPACKAGE CORE =
resolve_app_image_dir() {
  # $1 = jpackage --dest directory, $2 = --name value
  if [ -d "$1/$2.app" ]; then
    printf '%s\n' "$1/$2.app"
  elif [ -d "$1/$2" ]; then
    printf '%s\n' "$1/$2"
  else
    return 1
  fi
}

jpackage_base_args() {
  # Populates the global JPACKAGE_ARGS array with the shared option set.
  JPACKAGE_ARGS=(
    --name "${APP_DISPLAY_NAME}"
    --app-version "${PKG_VERSION}"
    --input "${PACKAGE_INPUT_DIR}"
    --main-jar "${MAIN_JAR_NAME}"
    --main-class "${MAIN_CLASS}"
    --vendor "wrouter"
    --description "wrouter - lightweight web route proxy"
    --java-options "-Dwrouter.home=\$APPDIR"
    --java-options "-Dfile.encoding=UTF-8"
    --java-options "-Djava.awt.headless=true"
  )
  if [ -n "${ICON_PATH}" ]; then
    JPACKAGE_ARGS+=(--icon "${ICON_PATH}")
  fi
  # A non-modular Spring Boot jar gives jpackage no module graph to analyse, so
  # by default it bundles a complete JDK runtime (~147 MB unpacked). Supplying
  # an explicit module list lets jpackage/jlink trim it. Opt-in, because a
  # module missing on a rarely exercised code path fails at runtime rather than
  # at build time. See docs/PACKAGING.md.
  if [ -n "${WROUTER_JPACKAGE_MODULES:-}" ]; then
    JPACKAGE_ARGS+=(--add-modules "${WROUTER_JPACKAGE_MODULES}")
    log_info "trimmed runtime modules: ${WROUTER_JPACKAGE_MODULES}"
  else
    log_info "runtime: full JDK (set WROUTER_JPACKAGE_MODULES to trim)"
  fi
  case "${OS_FAMILY}" in
    macos)
      JPACKAGE_ARGS+=(--mac-package-identifier "com.geek.webrouter")
      ;;
  esac
  return 0
}

# jpackage rejects several options when --type app-image is used: they only make
# sense for a native installer. Passing them to an app-image build aborts it with
# "Option [--linux-package-name] is not valid with type [app-image]" (Linux) or
# "Option [--win-menu] is not valid with type [app-image]" (Windows), which is
# exactly how the Linux and Windows app-images failed in CI. Keep them in a
# separate list that only build_installer consumes.
installer_only_args() {
  INSTALLER_ARGS=()
  case "${OS_FAMILY}" in
    linux)
      INSTALLER_ARGS+=(--linux-package-name "wrouter")
      ;;
    windows)
      INSTALLER_ARGS+=(--win-menu --win-shortcut --win-dir-chooser)
      INSTALLER_ARGS+=(--win-upgrade-uuid "${WROUTER_WIN_UPGRADE_UUID:-${DEFAULT_WIN_UPGRADE_UUID}}")
      ;;
  esac
  return 0
}

write_app_payload_extras() {
  # $1 = app-image directory. Adds the user-maintainable config template and a
  # build metadata file next to the application jar.
  image_dir=$1
  # jpackage lays the payload out differently per platform:
  #   macOS  <name>.app/Contents/app
  #   Linux  <name>/lib/app
  #   Windows <name>/app
  # Check all three, then fall back to locating the main jar so an unexpected
  # layout still gets the config template instead of a silent warning.
  payload_dir=""
  for candidate in "${image_dir}/Contents/app" "${image_dir}/lib/app" "${image_dir}/app"; do
    if [ -d "${candidate}" ]; then
      payload_dir="${candidate}"
      break
    fi
  done
  if [ -z "${payload_dir}" ]; then
    payload_dir=$(find "${image_dir}" -name "${MAIN_JAR_NAME}" -type f -print 2>/dev/null | head -n 1 || true)
    if [ -n "${payload_dir}" ]; then
      payload_dir=$(dirname -- "${payload_dir}")
    fi
  fi
  if [ -z "${payload_dir}" ]; then
    log_warn "could not locate the application payload directory inside ${image_dir}"
    return 0
  fi

  mkdir -p "${payload_dir}/config/routes"
  if [ -f "${PROJECT_ROOT}/src/main/resources/application.yml" ]; then
    cp "${PROJECT_ROOT}/src/main/resources/application.yml" "${payload_dir}/config/application.yml"
  fi

  cat > "${payload_dir}/config/README.md" <<'CONFIGEOF'
# wrouter 配置目录

本目录随程序一起分发，位于应用安装目录内，由启动参数
-Dwrouter.home=$APPDIR 指向，升级程序时不会被覆盖。

- application.yml：后端主配置（监听端口、Gateway 超时、Actuator 等）。
  端口默认 9999；改完重启应用生效。
- routes/：每条路由一个 <id>.json 文件，由管理后台写入，
  也可以在管理后台用「导入」功能批量导入。

在管理后台（http://127.0.0.1:9999/admin）新增或修改路由后，Gateway 路由与
本地端口代理会即时刷新，无需重启。
CONFIGEOF

  cat > "${payload_dir}/BUILD-INFO.txt" <<BUILDINFOEOF
app.version=${VERSION}
app.packageVersion=${PKG_VERSION}
app.platform=${PLATFORM}
app.installMode=${PACKAGE_TYPE}
app.buildTime=${BUILD_TIME}
app.jpackageName=${APP_DISPLAY_NAME}
BUILDINFOEOF

  log_info "config template: ${payload_dir}/config (application.yml + routes/)"
  return 0
}

prepare_package_input() {
  log_step "Preparing jpackage input"
  rm -rf "${PACKAGE_INPUT_DIR}"
  mkdir -p "${PACKAGE_INPUT_DIR}"
  cp "${SPRING_JAR}" "${PACKAGE_INPUT_DIR}/${MAIN_JAR_NAME}"
  log_info "input jar:  ${PACKAGE_INPUT_DIR}/${MAIN_JAR_NAME}"
  log_info "launcher:   ${MAIN_CLASS} (application class: ${APP_MAIN_CLASS})"
}

run_jpackage() {
  # run_jpackage <log_file> <args...>
  jp_log_file=$1
  shift
  set +e
  jpackage "$@" > "${jp_log_file}" 2>&1
  jp_status=$?
  set -e
  if [ "${jp_status}" -ne 0 ]; then
    log_error "jpackage failed (exit ${jp_status}); full log follows:"
    cat "${jp_log_file}" >&2
    die "jpackage failed"
  fi
  log_info "jpackage log: ${jp_log_file}"
  return 0
}

# ============================================================ APP-IMAGE MODE =
APP_IMAGE_DIR=""

build_app_image() {
  require_cmd jpackage "install a JDK 21 that ships jpackage"
  assert_native_platform "${PLATFORM}"
  prepare_icon
  if [ -n "${ICON_NOTE}" ]; then log_warn "${ICON_NOTE}"; fi
  prepare_package_input

  log_step "Building application image (jpackage --type app-image)"
  rm -rf "${JPACKAGE_WORK_DIR}"
  mkdir -p "${JPACKAGE_WORK_DIR}"

  jpackage_base_args
  run_jpackage "${JPACKAGE_WORK_DIR}/app-image.log" \
    "${JPACKAGE_ARGS[@]+"${JPACKAGE_ARGS[@]}"}" \
    --type app-image \
    --dest "${JPACKAGE_WORK_DIR}"

  raw_image=$(resolve_app_image_dir "${JPACKAGE_WORK_DIR}" "${APP_DISPLAY_NAME}") \
    || die "jpackage reported success but no app image was found in ${JPACKAGE_WORK_DIR}"

  image_name="wrouter-${VERSION}-${PLATFORM}"
  if [ "${OS_FAMILY}" = "macos" ]; then
    image_name="${image_name}.app"
  fi

  rm -rf "${JPACKAGE_WORK_DIR}/${image_name}"
  mv "${raw_image}" "${JPACKAGE_WORK_DIR}/${image_name}"
  APP_IMAGE_DIR="${JPACKAGE_WORK_DIR}/${image_name}"

  write_app_payload_extras "${APP_IMAGE_DIR}"

  log_step "Packaging application image"
  if [ "${OS_FAMILY}" = "linux" ]; then
    archive_kind="tar.gz"
    archive_path="${RELEASE_DIR}/wrouter-${VERSION}-${PLATFORM}-app.tar.gz"
  else
    archive_kind="zip"
    archive_path="${RELEASE_DIR}/wrouter-${VERSION}-${PLATFORM}-app.zip"
  fi

  make_archive "${archive_kind}" "${JPACKAGE_WORK_DIR}" "${image_name}" "${archive_path}"

  log_step "Application image ready"
  log_info "directory: ${APP_IMAGE_DIR}"
  log_info "archive:   ${archive_path}"
  log_info "size:      $(human_size "$(dir_size_bytes "${APP_IMAGE_DIR}")") (unpacked)"
  return 0
}

# ============================================================ INSTALLER MODE =
build_installer() {
  require_cmd jpackage "install a JDK 21 that ships jpackage"
  assert_native_platform "${PLATFORM}"
  prepare_icon
  if [ -n "${ICON_NOTE}" ]; then log_warn "${ICON_NOTE}"; fi
  prepare_package_input

  rm -rf "${JPACKAGE_WORK_DIR}"
  mkdir -p "${JPACKAGE_WORK_DIR}"
  jpackage_base_args
  installer_only_args

  case "${OS_FAMILY}" in
    macos)   installer_types="dmg" ;;
    linux)   installer_types="deb rpm" ;;
    windows) installer_types="exe" ;;
  esac

  built_any=false
  for installer_type in ${installer_types}; do
    log_step "Building ${installer_type} installer (jpackage --type ${installer_type})"
    set +e
    jpackage "${JPACKAGE_ARGS[@]+"${JPACKAGE_ARGS[@]}"}" \
      "${INSTALLER_ARGS[@]+"${INSTALLER_ARGS[@]}"}" \
      --type "${installer_type}" \
      --dest "${JPACKAGE_WORK_DIR}" > "${JPACKAGE_WORK_DIR}/installer-${installer_type}.log" 2>&1
    jp_status=$?
    set -e
    if [ "${jp_status}" -ne 0 ]; then
      log_warn "jpackage --type ${installer_type} failed (exit ${jp_status}); log follows:"
      sed 's/^/    /' "${JPACKAGE_WORK_DIR}/installer-${installer_type}.log" >&2 || true
      continue
    fi

    found=""
    for candidate in "${JPACKAGE_WORK_DIR}"/*."${installer_type}"; do
      if [ -f "${candidate}" ]; then
        found="${candidate}"
        break
      fi
    done
    if [ -z "${found}" ]; then
      log_warn "jpackage produced no .${installer_type} file"
      continue
    fi

    contract="${RELEASE_DIR}/wrouter-${VERSION}-${PLATFORM}.${installer_type}"
    cp "${found}" "${contract}"
    built_any=true
    log_info "installer: ${contract}"
  done

  if [ "${built_any}" != "true" ]; then
    die "no installer could be produced for ${PLATFORM}; see logs in ${JPACKAGE_WORK_DIR}"
  fi
  return 0
}

# ==================================================================== MAIN ==
case "${PACKAGE_TYPE}" in
  jar)       build_jar ;;
  app-image) build_app_image ;;
  installer) build_installer ;;
esac

log_step "Done: ${PACKAGE_TYPE} package for ${PLATFORM}"
ls -la "${RELEASE_DIR}" | sed 's/^/    /'
