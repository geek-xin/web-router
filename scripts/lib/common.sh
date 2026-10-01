#!/usr/bin/env bash
# ============================================================================
# wrouter packaging helpers
# Sourced by scripts/build-dist.sh, scripts/build-package.sh and
# scripts/build-release.sh. Never executed directly.
#
# Shell target: POSIX sh / bash 3.2 (macOS system bash). No bash 4 features.
# ============================================================================

set -eu

# ------------------------------------------------------------------ output --
log_step()  { printf '\n==> %s\n' "$*"; }
log_info()  { printf '    %s\n' "$*"; }
log_warn()  { printf 'warning: %s\n' "$*" >&2; }
log_error() { printf 'error: %s\n' "$*" >&2; }
die()       { log_error "$*"; exit 1; }

# ------------------------------------------------------------- environment --
project_root_from() {
  # $1 = path of a script living in <root>/scripts or <root>/scripts/lib
  script_dir=$(CDPATH= cd -- "$(dirname -- "$1")" && pwd)
  CDPATH= cd -- "${script_dir}/.." && pwd
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    if [ "$#" -ge 2 ]; then
      die "required command not found: $1 ($2)"
    fi
    die "required command not found: $1"
  fi
}

PYTHON_BIN=
detect_python() {
  PYTHON_BIN=
  for candidate in python3 python; do
    if command -v "${candidate}" >/dev/null 2>&1; then
      if "${candidate}" -c 'import sys, zlib, struct; sys.exit(0)' >/dev/null 2>&1; then
        PYTHON_BIN=${candidate}
        return 0
      fi
    fi
  done
  return 1
}

# ---------------------------------------------------------------- platform --
detect_host_platform() {
  host_os=$(uname -s)
  host_arch=$(uname -m)
  case "${host_os}" in
    Darwin)
      case "${host_arch}" in
        arm64)  printf 'macos-arm64\n' ;;
        x86_64) printf 'macos-x64\n' ;;
        *)      printf 'macos-%s\n' "${host_arch}" ;;
      esac
      ;;
    Linux)
      case "${host_arch}" in
        x86_64|amd64)  printf 'linux-x64\n' ;;
        aarch64|arm64) printf 'linux-arm64\n' ;;
        *)             printf 'linux-%s\n' "${host_arch}" ;;
      esac
      ;;
    MINGW*|MSYS*|CYGWIN*) printf 'windows-x64\n' ;;
    *) printf 'unknown-%s-%s\n' "${host_os}" "${host_arch}" ;;
  esac
}

assert_native_platform() {
  target=$1
  host=$(detect_host_platform)
  if [ "${target}" = "${host}" ]; then
    return 0
  fi
  if [ "${WROUTER_ALLOW_CROSS:-0}" = "1" ]; then
    log_warn "cross packaging '${target}' on '${host}' (WROUTER_ALLOW_CROSS=1)"
    return 0
  fi
  die "jpackage cannot cross-build: host is '${host}' but --platform is '${target}'. Run this on a matching runner, or set WROUTER_ALLOW_CROSS=1 to try anyway."
}

# ------------------------------------------------------------------- utils --
iso8601_now() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }

sha256_of() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    die "neither shasum nor sha256sum is available; cannot compute checksums"
  fi
}

file_size_bytes() { wc -c < "$1" | tr -d ' '; }

dir_size_bytes() { du -sk "$1" | awk '{print $1 * 1024}'; }

human_size() {
  awk -v b="$1" 'BEGIN {
    split("B KB MB GB TB", u, " ");
    i = 1;
    while (b >= 1024 && i < 5) { b = b / 1024; i++ }
    if (i == 1) { printf "%.0f %s", b, u[i] } else { printf "%.1f %s", b, u[i] }
  }'
}

make_archive() {
  # make_archive <zip|tar.gz> <parent_dir> <entry_name> <absolute_out_file>
  archive_kind=$1
  archive_parent=$2
  archive_entry=$3
  archive_out=$4

  case "${archive_out}" in
    /*) ;;
    *) die "internal error: archive output path must be absolute: ${archive_out}" ;;
  esac

  mkdir -p "$(dirname -- "${archive_out}")"
  rm -f "${archive_out}"

  case "${archive_kind}" in
    zip)
      if command -v zip >/dev/null 2>&1; then
        ( cd "${archive_parent}" && zip -qry "${archive_out}" "${archive_entry}" )
      else
        # Git Bash on the Windows runners ships no zip binary, so fall back to
        # Python (already required for icon rasterisation). Without this the
        # windows-x64 app-image silently produced no -app.zip.
        detect_python || die "neither zip nor python3 is available; cannot create ${archive_out}"
        "${PYTHON_BIN}" - "${archive_parent}" "${archive_entry}" "${archive_out}" <<'PYEOF'
import os, sys, zipfile
parent, entry, out = sys.argv[1], sys.argv[2], sys.argv[3]
root_dir = os.path.join(parent, entry)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, _dirs, files in os.walk(root_dir):
        for name in files:
            full = os.path.join(root, name)
            zf.write(full, os.path.relpath(full, parent))
PYEOF
      fi
      ;;
    tar.gz)
      require_cmd tar
      ( cd "${archive_parent}" && tar -czf "${archive_out}" "${archive_entry}" )
      ;;
    *)
      die "unsupported archive kind: ${archive_kind}"
      ;;
  esac

  [ -f "${archive_out}" ] || die "archive was not created: ${archive_out}"
}

# ---------------------------------------------------------------- metadata --
sanitize_pkg_version() {
  # jpackage and deb/rpm reject versions such as 1.3.0-SNAPSHOT; they want x.y.z
  raw=$1
  base=$(printf '%s' "${raw}" | sed -e 's/[-+].*$//' -e 's/[^0-9.].*$//')
  case "${base}" in
    [0-9]*.[0-9]*.[0-9]*) ;;
    [0-9]*.[0-9]*)        base="${base}.0" ;;
    [0-9]*)               base="${base}.0.0" ;;
    *)                    base="1.0.0" ;;
  esac
  printf '%s\n' "${base}"
}

read_target_metadata() {
  # Reads the Maven-filtered build metadata and sets APP_VERSION, APP_PLATFORM,
  # APP_INSTALL_MODE and APP_BUILD_TIME. Returns 1 when unavailable.
  props_file="${PROJECT_ROOT}/target/classes/version.properties"
  [ -f "${props_file}" ] || return 1

  APP_VERSION=$(sed -n 's/^app\.version=//p' "${props_file}" | head -n 1)
  APP_PLATFORM=$(sed -n 's/^app\.platform=//p' "${props_file}" | head -n 1)
  APP_INSTALL_MODE=$(sed -n 's/^app\.installMode=//p' "${props_file}" | head -n 1)
  APP_BUILD_TIME=$(sed -n 's/^app\.buildTime=//p' "${props_file}" | head -n 1)

  [ -n "${APP_VERSION}" ] || return 1
  case "${APP_VERSION}" in
    *'@'*) return 1 ;;
  esac
  return 0
}

resolve_app_version() {
  if [ -n "${WROUTER_VERSION_OVERRIDE:-}" ]; then
    printf '%s\n' "${WROUTER_VERSION_OVERRIDE}"
    return 0
  fi
  if read_target_metadata; then
    printf '%s\n' "${APP_VERSION}"
    return 0
  fi
  require_cmd mvn "needed to resolve the project version"
  mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | tr -d '\r' | tail -n 1
}

# ---------------------------------------------------------------- summary ---
print_asset_summary() {
  # print_asset_summary <release_dir>
  summary_dir=$1
  [ -d "${summary_dir}" ] || return 0
  log_step "Assets in ${summary_dir}"
  printf '    %-50s %11s  %s\n' "FILE" "SIZE" "SHA256 (first 16)"
  for asset in "${summary_dir}"/*; do
    [ -f "${asset}" ] || continue
    case "${asset}" in
      *SHA256SUMS.txt|*release-manifest.txt) continue ;;
    esac
    printf '    %-50s %11s  %s\n' \
      "$(basename -- "${asset}")" \
      "$(human_size "$(file_size_bytes "${asset}")")" \
      "$(sha256_of "${asset}" | cut -c1-16)"
  done
}
