#!/usr/bin/env sh
# install.sh — agent-system CLI installer
#
# Usage (one-liner):
#   curl -fsSL https://raw.githubusercontent.com/David200308/rag-agent-system/main/cli-go/install.sh | sh
#
# Options via env vars:
#   VERSION   — install a specific version, e.g. VERSION=v1.2.0 curl ... | sh
#   INSTALL_DIR — override install directory (default: /usr/local/bin or ~/.local/bin)

set -e

REPO="David200308/rag-agent-system"
BINARY="agent-system"
RELEASES="https://github.com/${REPO}/releases"

# ── Helpers ───────────────────────────────────────────────────────────────────

info()  { printf '\033[1m[info]\033[0m  %s\n' "$*"; }
ok()    { printf '\033[32m[ok]\033[0m    %s\n' "$*"; }
err()   { printf '\033[31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

need() {
  command -v "$1" >/dev/null 2>&1 || err "required tool not found: $1"
}

# ── Detect OS ─────────────────────────────────────────────────────────────────

detect_os() {
  case "$(uname -s)" in
    Linux)  echo "linux"  ;;
    Darwin) echo "darwin" ;;
    *)      err "Unsupported OS: $(uname -s). Download manually from ${RELEASES}" ;;
  esac
}

# ── Detect architecture ───────────────────────────────────────────────────────

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64)   echo "amd64" ;;
    aarch64|arm64)  echo "arm64" ;;
    *)              err "Unsupported architecture: $(uname -m)" ;;
  esac
}

# ── Resolve latest version from GitHub ───────────────────────────────────────

latest_version() {
  need curl
  url=$(curl -fsSLI -o /dev/null -w '%{url_effective}' \
    "https://github.com/${REPO}/releases/latest")
  # url_effective is the redirected URL: .../releases/tag/v1.2.3
  echo "${url##*/}"
}

# ── Pick install directory ────────────────────────────────────────────────────

pick_install_dir() {
  if [ -n "${INSTALL_DIR}" ]; then
    echo "${INSTALL_DIR}"
    return
  fi
  # Prefer /usr/local/bin if writable, otherwise ~/.local/bin
  if [ -w "/usr/local/bin" ]; then
    echo "/usr/local/bin"
  elif [ -w "/usr/bin" ]; then
    echo "/usr/bin"
  else
    echo "${HOME}/.local/bin"
  fi
}

# ── Main ──────────────────────────────────────────────────────────────────────

main() {
  need curl
  need tar

  OS="$(detect_os)"
  ARCH="$(detect_arch)"

  VERSION="${VERSION:-$(latest_version)}"
  # Normalize: ensure leading 'v'
  case "${VERSION}" in
    v*) ;;
    *)  VERSION="v${VERSION}" ;;
  esac

  INSTALL_DIR="$(pick_install_dir)"
  DEST="${INSTALL_DIR}/${BINARY}"

  ARCHIVE="${BINARY}_${VERSION}_${OS}_${ARCH}.tar.gz"
  CHECKSUM_FILE="${BINARY}_${VERSION}_checksums.txt"
  BASE_URL="${RELEASES}/download/${VERSION}"

  info "Installing ${BINARY} ${VERSION} (${OS}/${ARCH})"
  info "Download: ${BASE_URL}/${ARCHIVE}"

  TMPDIR="$(mktemp -d)"
  trap 'rm -rf "${TMPDIR}"' EXIT

  # ── Download archive + checksums ─────────────────────────────────────────
  info "Downloading archive…"
  curl -fsSL --progress-bar \
    "${BASE_URL}/${ARCHIVE}" -o "${TMPDIR}/${ARCHIVE}"

  info "Downloading checksums…"
  curl -fsSL \
    "${BASE_URL}/${CHECKSUM_FILE}" -o "${TMPDIR}/${CHECKSUM_FILE}"

  # ── Verify checksum ───────────────────────────────────────────────────────
  info "Verifying checksum…"
  cd "${TMPDIR}"
  if command -v sha256sum >/dev/null 2>&1; then
    grep "${ARCHIVE}" "${CHECKSUM_FILE}" | sha256sum --check --status \
      || err "Checksum mismatch — aborting"
  elif command -v shasum >/dev/null 2>&1; then
    grep "${ARCHIVE}" "${CHECKSUM_FILE}" | shasum -a 256 --check --status \
      || err "Checksum mismatch — aborting"
  else
    info "Warning: no sha256sum/shasum found — skipping checksum verification"
  fi
  cd - >/dev/null

  # ── Extract ───────────────────────────────────────────────────────────────
  info "Extracting…"
  tar -xzf "${TMPDIR}/${ARCHIVE}" -C "${TMPDIR}"

  # ── Install ───────────────────────────────────────────────────────────────
  mkdir -p "${INSTALL_DIR}"
  if [ -w "${INSTALL_DIR}" ]; then
    mv "${TMPDIR}/${BINARY}" "${DEST}"
  else
    info "Need sudo to write to ${INSTALL_DIR}"
    sudo mv "${TMPDIR}/${BINARY}" "${DEST}"
  fi
  chmod +x "${DEST}"

  # ── Verify ────────────────────────────────────────────────────────────────
  ok "Installed ${DEST}"
  ok "Version: $(${DEST} --version 2>&1)"

  # Warn if install dir is not in PATH
  case ":${PATH}:" in
    *":${INSTALL_DIR}:"*) ;;
    *)
      printf '\n\033[33m[warn]\033[0m  %s is not in your PATH.\n' "${INSTALL_DIR}"
      printf '        Add this to your shell profile:\n'
      printf '          export PATH="%s:$PATH"\n\n' "${INSTALL_DIR}"
      ;;
  esac
}

main "$@"
