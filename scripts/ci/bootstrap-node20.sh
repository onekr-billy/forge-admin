#!/usr/bin/env bash
set -euo pipefail

readonly FORGE_NODE_VERSION="20.19.5"
readonly FORGE_REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly FORGE_TOOL_ROOT="${FORGE_REPOSITORY_ROOT}/.ci-tools"

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) forge_node_platform="linux-x64" ;;
  Linux-aarch64|Linux-arm64) forge_node_platform="linux-arm64" ;;
  Darwin-x86_64) forge_node_platform="darwin-x64" ;;
  Darwin-arm64) forge_node_platform="darwin-arm64" ;;
  *)
    echo "Unsupported Node.js bootstrap platform: $(uname -s)-$(uname -m)" >&2
    exit 1
    ;;
esac

forge_archive="node-v${FORGE_NODE_VERSION}-${forge_node_platform}.tar.gz"
forge_install_dir="${FORGE_TOOL_ROOT}/node-v${FORGE_NODE_VERSION}-${forge_node_platform}"
if [[ -x "${forge_install_dir}/bin/node" ]] \
  && [[ "$("${forge_install_dir}/bin/node" --version)" == "v${FORGE_NODE_VERSION}" ]]; then
  printf '%s\n' "${forge_install_dir}/bin"
  exit 0
fi

forge_download_dir="$(mktemp -d)"
trap 'rm -rf "${forge_download_dir}"' EXIT
forge_base_url="https://nodejs.org/dist/v${FORGE_NODE_VERSION}"

download_file() {
  local source_url="$1"
  local target_file="$2"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "${source_url}" --output "${target_file}"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "${source_url}" --output-document="${target_file}"
  else
    echo "Node.js bootstrap requires curl or wget." >&2
    exit 1
  fi
}

download_file "${forge_base_url}/${forge_archive}" "${forge_download_dir}/${forge_archive}"
download_file "${forge_base_url}/SHASUMS256.txt" "${forge_download_dir}/SHASUMS256.txt"

forge_expected_sha="$(awk -v archive="${forge_archive}" '$2 == archive { print $1 }' "${forge_download_dir}/SHASUMS256.txt")"
if [[ -z "${forge_expected_sha}" ]]; then
  echo "Node.js checksum manifest does not contain ${forge_archive}." >&2
  exit 1
fi
if command -v sha256sum >/dev/null 2>&1; then
  forge_actual_sha="$(sha256sum "${forge_download_dir}/${forge_archive}" | awk '{ print $1 }')"
else
  forge_actual_sha="$(shasum -a 256 "${forge_download_dir}/${forge_archive}" | awk '{ print $1 }')"
fi
if [[ "${forge_actual_sha}" != "${forge_expected_sha}" ]]; then
  echo "Node.js archive checksum verification failed." >&2
  exit 1
fi

mkdir -p "${FORGE_TOOL_ROOT}"
tar -xzf "${forge_download_dir}/${forge_archive}" -C "${FORGE_TOOL_ROOT}"
printf '%s\n' "${forge_install_dir}/bin"
