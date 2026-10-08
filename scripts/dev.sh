#!/usr/bin/env bash
# Run the Android or Worker toolchain without changing the caller's shell.
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
action="${1:-}"
if [[ $# -gt 0 ]]; then shift; fi
cd "$project_root"

case "$action" in
  setup)
    exec "$project_root/scripts/setup-linux.sh" "$@"
    ;;
  android)
    if [[ -z "${JAVA_HOME:-}" ]]; then
      for candidate in "$HOME/android-studio/jbr" /opt/android-studio/jbr /snap/android-studio/current/android-studio/jbr; do
        if [[ -x "$candidate/bin/java" ]]; then
          export JAVA_HOME="$candidate"
          break
        fi
      done
    fi
    if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null; then
      echo 'Set JAVA_HOME to JDK 17+ or install Android Studio.' >&2
      exit 1
    fi
    if [[ $# -eq 0 ]]; then set -- :app:assembleDebug :app:testDebugUnitTest; fi
    exec "$project_root/gradlew" "$@"
    ;;
  worker)
    if [[ -x "$project_root/.tools/node/bin/node" ]]; then
      export PATH="$project_root/.tools/node/bin:$PATH"
    fi
    if ! command -v node >/dev/null || ! node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit(major > 22 || (major === 22 && minor >= 22) ? 0 : 1)'; then
      echo 'Node.js 22.22+ is required. Run ./scripts/dev.sh setup.' >&2
      exit 1
    fi
    if [[ -x "$project_root/.tools/pnpm/node_modules/.bin/pnpm" ]]; then
      pnpm_command="$project_root/.tools/pnpm/node_modules/.bin/pnpm"
    elif command -v pnpm >/dev/null; then
      pnpm_command="$(command -v pnpm)"
    else
      echo 'pnpm is required. Run ./scripts/dev.sh setup.' >&2
      exit 1
    fi
    export PATH="$(dirname "$pnpm_command"):$PATH"
    if [[ $# -eq 0 ]]; then set -- run check; fi
    exec "$pnpm_command" --dir "$project_root/cloudflare" "$@"
    ;;
  *)
    echo 'Usage: ./scripts/dev.sh setup | android [Gradle tasks] | worker [pnpm arguments]' >&2
    exit 2
    ;;
esac
