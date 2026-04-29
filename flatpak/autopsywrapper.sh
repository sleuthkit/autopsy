#!/bin/bash
# Ensure Autopsy's tmp dir exists before launch (mirrors Snap wrapper behaviour).
mkdir -p "${XDG_RUNTIME_DIR:-/tmp}/autopsy-tmp"
exec /app/autopsy/bin/autopsy "$@"
