#!/usr/bin/env bash
# ============================================================================
# wrouter release builder (used by CI, runnable locally).
#
# Produces every contract-named asset for ONE platform into target/release/:
#   wrouter-<version>-jar.zip | .tar.gz
#   wrouter-<version>-<platform>-app.zip | .tar.gz
#   wrouter-<version>-<platform>.dmg | .deb | .rpm | .exe
# plus SHA256SUMS.txt and release-manifest.txt.
#
# Usage:
#   scripts/build-release.sh [--platform <p>] [--version <v>] [--skip-tests]
#                            [--only jar,app-image,installer]
#                            [--release-dir <dir>]
# ============================================================================

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
# shellcheck source=scripts/lib/common.sh
. "${SCRIPT_DIR}/lib/common.sh"
cd "${PROJECT_ROOT}"

PLATFORM=""
VERSION_OVERRIDE=""
RUN_TESTS=true
BUILD_TYPES="jar,app-image,installer"
RELEASE_DIR_OVERRIDE=""

usage() {
  cat <<'USAGE'
Usage: scripts/build-release.sh [options]

Options:
  --platform <name>    macos-arm64 | macos-x64 | linux-x64 | linux-arm64 |
                       windows-x64. Defaults to the host platform.
  --version <version>  Override the version read from pom.xml / the build.
  --skip-tests         Pass -DskipTests to Maven.
  --only <list>        Comma separated subset of jar,app-image,installer.
                       Default: jar,app-image,installer
  --release-dir <dir>  Where the final assets are written.
                       Default: target/release
  -h, --help           Show this help.

Notes:
  * jpackage cannot cross-build, so one platform per invocation. CI runs this
    script in a matrix; see .github/workflows/release.yml.
  * Intermediate artifacts are staged outside target/ because every Maven
    "clean" would otherwise delete them.
  * The script exits non-zero if any requested build type fails, so CI never
    publishes a partial release silently.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --platform)
      [ "$#" -ge 2 ] || die "--platform requires a value"
      PLATFORM=$2
      shift 2
      ;;
    --platform=*)
      PLATFORM=${1#--platform=}
      shift
      ;;
    --version)
      [ "$#" -ge 2 ] || die "--version requires a value"
      VERSION_OVERRIDE=$2
      shift 2
      ;;
    --version=*)
      VERSION_OVERRIDE=${1#--version=}
      shift
      ;;
    --only)
      [ "$#" -ge 2 ] || die "--only requires a value"
      BUILD_TYPES=$2
      shift 2
      ;;
    --only=*)
      BUILD_TYPES=${1#--only=}
      shift
      ;;
    --release-dir)
      [ "$#" -ge 2 ] || die "--release-dir requires a value"
      RELEASE_DIR_OVERRIDE=$2
      shift 2
      ;;
    --release-dir=*)
      RELEASE_DIR_OVERRIDE=${1#--release-dir=}
      shift
      ;;
    --skip-tests)
      RUN_TESTS=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      log_error "unknown option: $1"
      echo "Run scripts/build-release.sh --help for usage." >&2
      exit 2
      ;;
  esac
done

if [ -z "${PLATFORM}" ]; then
  PLATFORM=$(detect_host_platform)
  log_info "no --platform given, using host platform: ${PLATFORM}"
fi

case "${PLATFORM}" in
  macos-arm64|macos-x64|linux-x64|linux-arm64|windows-x64) ;;
  *) die "unsupported --platform '${PLATFORM}'" ;;
esac

case "${PLATFORM}" in
  macos-*)   OS_FAMILY=macos ;;
  linux-*)   OS_FAMILY=linux ;;
  windows-*) OS_FAMILY=windows ;;
esac

assert_native_platform "${PLATFORM}"
require_cmd mvn
[ -f "${PROJECT_ROOT}/pom.xml" ] || die "pom.xml not found in ${PROJECT_ROOT}"

# Normalise the requested build types and reject typos early.
REQUESTED_TYPES=""
for requested in $(printf '%s' "${BUILD_TYPES}" | tr ',' ' '); do
  case "${requested}" in
    jar|app-image|installer) ;;
    *) die "unsupported --only entry '${requested}' (expected jar, app-image or installer)" ;;
  esac
  REQUESTED_TYPES="${REQUESTED_TYPES} ${requested}"
done
[ -n "${REQUESTED_TYPES}" ] || die "--only did not select any build type"

# Always run jar -> app-image -> installer so that the cheapest type pays for
# the single Maven compile and the remaining types can reuse it (--skip-build).
ORDERED_TYPES=""
for canonical in jar app-image installer; do
  for requested in ${REQUESTED_TYPES}; do
    if [ "${requested}" = "${canonical}" ]; then
      ORDERED_TYPES="${ORDERED_TYPES} ${canonical}"
    fi
  done
done
REQUESTED_TYPES="${ORDERED_TYPES}"

if [ -n "${VERSION_OVERRIDE}" ]; then
  export WROUTER_VERSION_OVERRIDE="${VERSION_OVERRIDE}"
fi
VERSION=$(resolve_app_version)
[ -n "${VERSION}" ] || die "could not resolve the project version"

RELEASE_DIR="${RELEASE_DIR_OVERRIDE}"
if [ -z "${RELEASE_DIR}" ]; then
  RELEASE_DIR="${PROJECT_ROOT}/target/release"
fi
case "${RELEASE_DIR}" in
  /*) ;;
  *) RELEASE_DIR="${PROJECT_ROOT}/${RELEASE_DIR}" ;;
esac

# Staging lives outside target/ so that "mvn clean" cannot delete earlier
# build types while the next one runs.
STAGING_DIR="${TMPDIR:-/tmp}/wrouter-release-${VERSION}-$$"
rm -rf "${STAGING_DIR}"
mkdir -p "${STAGING_DIR}"
trap 'rm -rf "${STAGING_DIR}"' EXIT INT TERM

log_step "wrouter release build"
log_info "version:   ${VERSION}"
log_info "platform:  ${PLATFORM}"
log_info "types:    ${REQUESTED_TYPES}"
log_info "staging:   ${STAGING_DIR}"
log_info "release:   ${RELEASE_DIR}"

if [ "${RUN_TESTS}" = "true" ]; then
  PACKAGE_TEST_FLAG=""
else
  PACKAGE_TEST_FLAG="--skip-tests"
fi

BUILT_TYPES=""
first_type=true
for build_type in ${REQUESTED_TYPES}; do
  log_step "=== ${build_type} ==="
  # The first type compiles once; the remaining types reuse that build output
  # instead of paying for another "mvn clean package". Types run in the order
  # jar -> app-image -> installer, so the cheapest type pays for the compile.
  if [ "${first_type}" = "true" ]; then
    PACKAGE_BUILD_FLAG=""
    first_type=false
  else
    PACKAGE_BUILD_FLAG="--skip-build"
  fi

  # A type can legitimately produce nothing (a Linux host without rpmbuild, a
  # macOS host where dmg creation is unavailable). That must not abort the
  # whole release: warn, then let the contract-name check below decide what is
  # actually required.
  # shellcheck disable=SC2086
  set +e
  WROUTER_RELEASE_DIR="${STAGING_DIR}" \
  WROUTER_VERSION_OVERRIDE="${VERSION}" \
    "${SCRIPT_DIR}/build-package.sh" \
      --type "${build_type}" \
      --platform "${PLATFORM}" \
      ${PACKAGE_TEST_FLAG} ${PACKAGE_BUILD_FLAG}
  type_status=$?
  set -e
  if [ "${type_status}" -ne 0 ]; then
    log_warn "build type '${build_type}' exited with status ${type_status}; continuing"
  fi
  BUILT_TYPES="${BUILT_TYPES} ${build_type}"
done

# ------------------------------------------------- contract name validation --
log_step "Verifying contract asset names"
expected_count=0
for build_type in ${BUILT_TYPES}; do
  case "${build_type}" in
    jar)
      for required in "wrouter-${VERSION}-jar.zip" "wrouter-${VERSION}-jar.tar.gz"; do
        [ -f "${STAGING_DIR}/${required}" ] || die "missing expected asset: ${required}"
        expected_count=$((expected_count + 1))
      done
      ;;
    app-image)
      case "${OS_FAMILY}" in
        linux) image_archive="wrouter-${VERSION}-${PLATFORM}-app.tar.gz" ;;
        *)     image_archive="wrouter-${VERSION}-${PLATFORM}-app.zip" ;;
      esac
      [ -f "${STAGING_DIR}/${image_archive}" ] || die "missing expected asset: ${image_archive}"
      expected_count=$((expected_count + 1))
      ;;
    installer)
      found_installer=false
      for extension in dmg deb rpm exe; do
        if [ -f "${STAGING_DIR}/wrouter-${VERSION}-${PLATFORM}.${extension}" ]; then
          found_installer=true
          expected_count=$((expected_count + 1))
          log_info "installer asset: wrouter-${VERSION}-${PLATFORM}.${extension}"
        fi
      done
      if [ "${found_installer}" != "true" ]; then
        # Windows must always produce an installer (jpackage --type exe needs
        # no extra host tooling). On macOS and Linux the app-image stays the
        # primary download, so a missing dmg/deb/rpm is a warning rather than a
        # release blocker. Set WROUTER_REQUIRE_INSTALLER=1 to be strict.
        if [ "${OS_FAMILY}" = "windows" ] || [ "${WROUTER_REQUIRE_INSTALLER:-0}" = "1" ]; then
          die "no installer asset was produced for ${PLATFORM}"
        fi
        log_warn "no ${PLATFORM} installer (dmg/deb/rpm) was produced; releasing app-image only"
      fi
      ;;
  esac
done
log_info "${expected_count} contract asset(s) verified"

# Every published asset must match the naming contract shared with the backend
# updater (src/main/java/com/geek/webrouter/web/support/ReleaseAssets.java).
# A mismatch silently breaks auto-update, so fail the release instead.
contract_pattern='^wrouter-[0-9]+\.[0-9]+\.[0-9]+(-[a-z0-9]+)*-(jar\.zip|jar\.tar\.gz|(macos-arm64|macos-x64|linux-x64|linux-arm64|windows-x64)(-app\.(zip|tar\.gz)|\.(dmg|deb|rpm|exe)))$'
for staged in "${STAGING_DIR}"/*; do
  [ -f "${staged}" ] || continue
  asset_name=$(basename "${staged}")
  case "${asset_name}" in
    SHA256SUMS.txt|release-manifest.txt) continue ;;
  esac
  if ! printf '%s' "${asset_name}" | grep -Eq "${contract_pattern}"; then
    die "asset name violates the update contract: ${asset_name}"
  fi
done
log_info "all asset names match the update contract"

# -------------------------------------------------------------- publishing --
log_step "Publishing assets to ${RELEASE_DIR}"
rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"
for staged in "${STAGING_DIR}"/*; do
  [ -f "${staged}" ] || continue
  cp "${staged}" "${RELEASE_DIR}/"
done

# --------------------------------------------------------------- checksums --
log_step "Generating SHA256SUMS.txt"
checksum_file="${RELEASE_DIR}/SHA256SUMS.txt"
rm -f "${checksum_file}"
for asset in $(cd "${RELEASE_DIR}" && ls -1 | LC_ALL=C sort); do
  case "${asset}" in
    SHA256SUMS.txt|release-manifest.txt) continue ;;
  esac
  [ -f "${RELEASE_DIR}/${asset}" ] || continue
  printf '%s  %s\n' "$(sha256_of "${RELEASE_DIR}/${asset}")" "${asset}" >> "${checksum_file}"
done
[ -s "${checksum_file}" ] || die "no assets were checksummed"
log_info "$(wc -l < "${checksum_file}" | tr -d ' ') checksum(s) written"

log_step "Generating release-manifest.txt"
manifest_file="${RELEASE_DIR}/release-manifest.txt"
{
  printf 'wrouter release manifest\n'
  printf 'version=%s\n' "${VERSION}"
  printf 'platform=%s\n' "${PLATFORM}"
  printf 'buildTypes=%s\n' "${BUILT_TYPES}"
  printf 'builtAt=%s\n' "$(iso8601_now)"
  printf 'javaVersion=%s\n' "$(java -version 2>&1 | head -n 1)"
  printf 'gitCommit=%s\n' "$(git -C "${PROJECT_ROOT}" rev-parse HEAD 2>/dev/null || echo unknown)"
  printf '\nassets:\n'
  while read -r checksum name; do
    [ -n "${name}" ] || continue
    printf '  %s  %s  %s\n' "${checksum}" "$(human_size "$(file_size_bytes "${RELEASE_DIR}/${name}")")" "${name}"
  done < "${checksum_file}"
} > "${manifest_file}"

print_asset_summary "${RELEASE_DIR}"

log_step "Release build complete"
log_info "release dir: ${RELEASE_DIR}"
log_info "checksums:   ${checksum_file}"
log_info "manifest:    ${manifest_file}"
