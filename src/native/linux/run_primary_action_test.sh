#!/bin/bash
# Use a private session bus so the test does not register with the user's desktop.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN="$(mktemp)"
trap 'rm -f "$BIN"' EXIT

gcc -O2 -g -Wall -Wextra -Werror -Wno-unused-parameter \
    $(pkg-config --cflags libsystemd) \
    "$SCRIPT_DIR/sni.c" "$SCRIPT_DIR/test_sni_primary_action.c" \
    $(pkg-config --libs libsystemd) -lpthread -lm -ldl -o "$BIN"
dbus-run-session -- "$BIN"
