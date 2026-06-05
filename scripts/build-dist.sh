#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

RUN_TESTS=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-tests)
      RUN_TESTS=true
      shift
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/build-dist.sh [--with-tests]

Compile the Maven project, package the Spring Boot jar, and create a tar.gz
archive under target/dist/. Tests are skipped by default so the script can be
used as a packaging command; pass --with-tests to run the full Maven test phase.

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

MVN_ARGS=(clean package)
if [[ "${RUN_TESTS}" != "true" ]]; then
  MVN_ARGS+=(-DskipTests)
fi

echo "==> Building project: mvn ${MVN_ARGS[*]}"
mvn "${MVN_ARGS[@]}"

ARTIFACT_ID="$(mvn help:evaluate -Dexpression=project.artifactId -q -DforceStdout)"
VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"
APP_NAME="${ARTIFACT_ID}-${VERSION}"
DIST_ROOT="${PROJECT_ROOT}/target/dist"
STAGING_DIR="${DIST_ROOT}/${APP_NAME}"
ARCHIVE_PATH="${DIST_ROOT}/${APP_NAME}.tar.gz"
JAR_PATH="${PROJECT_ROOT}/target/${APP_NAME}.jar"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Expected jar was not found: ${JAR_PATH}" >&2
  exit 1
fi

rm -rf "${STAGING_DIR}"
mkdir -p "${STAGING_DIR}/config/routes"

cp "${JAR_PATH}" "${STAGING_DIR}/"
for doc in README.md USAGE.md CHANGELOG.md; do
  if [[ -f "${doc}" ]]; then
    cp "${doc}" "${STAGING_DIR}/"
  fi
done
cat > "${STAGING_DIR}/run.sh" <<RUNEOF
#!/usr/bin/env bash
set -euo pipefail
cd "\$(dirname "\${BASH_SOURCE[0]}")"
exec java -jar "${APP_NAME}.jar" "\$@"
RUNEOF
chmod +x "${STAGING_DIR}/run.sh"

rm -f "${ARCHIVE_PATH}"
tar -C "${DIST_ROOT}" -czf "${ARCHIVE_PATH}" "${APP_NAME}"

echo "==> Archive created: ${ARCHIVE_PATH}"
