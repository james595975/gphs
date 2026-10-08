#!/usr/bin/env bash
# Install a project-local Node/pnpm toolchain, then restore locked dependencies.
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tools_dir="$project_root/.tools"
node_version=24.19.0
pnpm_version=11.25.0
case "$(uname -s):$(uname -m)" in
  Linux:x86_64) node_arch=x64 ;;
  Linux:aarch64|Linux:arm64) node_arch=arm64 ;;
  *) echo 'This setup script supports Linux x64 and arm64.' >&2; exit 1 ;;
esac
for tool in curl tar sha256sum; do
  command -v "$tool" >/dev/null || { echo "Missing command: $tool" >&2; exit 1; }
done
mkdir -p "$tools_dir"
if [[ ! -x "$tools_dir/node/bin/node" ]]; then
  if [[ -e "$tools_dir/node" ]]; then
    echo 'An incomplete .tools/node directory exists; inspect it before retrying.' >&2
    exit 1
  fi
  download_dir="$(mktemp -d "$tools_dir/node-download.XXXXXX")"
  trap 'rm -rf "$download_dir"' EXIT
  archive="node-v${node_version}-linux-${node_arch}.tar.xz"
  download_base="https://nodejs.org/dist/v${node_version}"
  curl --fail --location --retry 3 "$download_base/$archive" -o "$download_dir/$archive"
  curl --fail --location --retry 3 "$download_base/SHASUMS256.txt" -o "$download_dir/SHASUMS256.txt"
  awk -v filename="$archive" '$2 == filename { print }' "$download_dir/SHASUMS256.txt" > "$download_dir/node.sha256"
  [[ "$(wc -l < "$download_dir/node.sha256")" -eq 1 ]]
  (cd "$download_dir" && sha256sum --check node.sha256)
  mkdir "$download_dir/node"
  tar -xJf "$download_dir/$archive" --strip-components=1 -C "$download_dir/node"
  mv "$download_dir/node" "$tools_dir/node"
fi
[[ "$("$tools_dir/node/bin/node" --version)" == "v$node_version" ]] || {
  echo "Expected Node v$node_version in .tools/node." >&2
  exit 1
}
export PATH="$tools_dir/node/bin:$PATH"
npm install --prefix "$tools_dir/pnpm" --no-save --no-package-lock --no-audit --no-fund "pnpm@$pnpm_version"
"$tools_dir/pnpm/node_modules/.bin/pnpm" --dir "$project_root/cloudflare" install --frozen-lockfile
echo 'Toolchain ready. Run ./scripts/dev.sh worker or ./scripts/dev.sh android.'
