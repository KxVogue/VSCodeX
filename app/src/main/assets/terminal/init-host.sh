#!/bin/sh
#
# init-host.sh — runs on the Android host (outside proot), sets up the
# proot environment and launches Alpine Linux via proot.
#
# Environment variables provided by Session.kt:
#   PROOT      absolute path to the proot binary
#   ALPINE     absolute path to the Alpine rootfs directory
#   PREFIX     absolute path to the VSX usr prefix (bind-mounted into proot)
#   HOME       absolute path to the home directory
#   PROOT_TMP_DIR  per-session temp directory
#

# ── Stage helper tools into PREFIX/bin ───────────────────────────────────────
# PREFIX/bin is the directory that CONTAINS this init-host script.
# We resolve it from $0 so it works regardless of how sh was invoked.
_SCRIPT_DIR="$(cd "$(dirname "$0")" 2>/dev/null && pwd)"

# Deploy pkg if not already there or if it changed (compare size as quick check)
_deploy() {
    local src="$1" dst="$2"
    if [ -f "$src" ]; then
        cp "$src" "$dst" && chmod +x "$dst"
    fi
}

_deploy "${_SCRIPT_DIR}/pkg"  "${PREFIX}/bin/pkg"
_deploy "${_SCRIPT_DIR}/init" "${PREFIX}/bin/init"

# ── proot bind arguments ──────────────────────────────────────────────────────
# Required: /dev /sys /proc for system calls, /sdcard+/storage for files,
# PREFIX so our usr packages are available inside Alpine at the same path.
PROOT_ARGS="\
  -r ${ALPINE} \
  -0 \
  -b /dev/ \
  -b /sys/ \
  -b /proc/ \
  -b /sdcard \
  -b /storage \
  -b ${PREFIX} \
  -w /home \
  --kill-on-exit \
  --link2symlink"

# ── Launch Alpine ─────────────────────────────────────────────────────────────
exec "${PROOT}" ${PROOT_ARGS} /bin/sh "${PREFIX}/bin/init" "$@"
