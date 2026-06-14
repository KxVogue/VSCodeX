#!/bin/sh
#
# init.sh — Alpine init script, runs INSIDE the proot environment.
# Called by init-host.sh as: /bin/sh $PREFIX/bin/init [args...]
#

# ── Core environment ──────────────────────────────────────────────────────────
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PREFIX}/bin
export HOME=/home
export TMPDIR=/tmp
export TERM=xterm-256color
export COLORTERM=truecolor
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export PROMPT_DIRTRIM=2
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@vcspace \[\033[39m\]\w \[\033[0m\]\$ "
START_SHELL="bash"

# ── Make /tmp usable ──────────────────────────────────────────────────────────
mkdir -p /tmp && chmod 1777 /tmp 2>/dev/null || true

# ── Android linker config shim ────────────────────────────────────────────────
# Silences "WARNING: linker: libdvm.so has text relocations" type messages.
if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig 2>/dev/null && touch /linkerconfig/ld.config.txt 2>/dev/null || true
fi

# ── Deploy pkg into Alpine PATH ───────────────────────────────────────────────
# ${PREFIX}/bin/pkg was staged by init-host.sh (host side).
# Copy it into Alpine's /usr/local/bin so it's on PATH without needing PREFIX.
if [ -f "${PREFIX}/bin/pkg" ] && [ ! -x /usr/local/bin/pkg ]; then
    cp "${PREFIX}/bin/pkg" /usr/local/bin/pkg 2>/dev/null && chmod +x /usr/local/bin/pkg 2>/dev/null || true
fi

# Create apt / apt-get symlinks for Debian-muscle-memory users
for _alias in apt apt-get; do
    [ -f /usr/local/bin/$_alias ] || ln -sf /usr/local/bin/pkg /usr/local/bin/$_alias 2>/dev/null || true
done

# ── Bootstrap packages ────────────────────────────────────────────────────────
_REQUIRED="bash nano sudo file"
_MISSING=""
for _pkg in $_REQUIRED; do
    apk info -e "$_pkg" >/dev/null 2>&1 || _MISSING="$_MISSING $_pkg"
done

if [ -n "$_MISSING" ]; then
    printf "\e[34;1m[*]\e[0m Installing bootstrap packages...\n"
    apk update -q 2>/dev/null || true
    # shellcheck disable=SC2086
    apk add -q $_MISSING 2>/dev/null || apk add $_MISSING || true
    printf "\e[32;1m[+]\e[0m Ready!  Use \e[32mpkg install <name>\e[0m to add more packages.\n"
fi

# ── Launch shell or run command ───────────────────────────────────────────────
if [ "$#" -eq 0 ]; then
    # Interactive shell: prefer bash, fall back to sh
    if command -v bash >/dev/null 2>&1; then
        exec bash --login
    else
        exec sh
    fi
else
    exec "$@"
fi
