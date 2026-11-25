#!/usr/bin/env bash
# Lays out what Kirmod Extras compiles against in build/kirmod/ for the MCPCommit action:
#   build/kirmod/libs     Kirmod's src/ plus its ci/libs/ (CommandLib stubs), compiled but never shipped
#   build/kirmod/patches  Kirmod's client patches (Tessellator's constructor made protected for KirTessellator)
# Run it before building with omnimcp.json.
#
# KIRMOD_DIR: checkout of the Kirmod repository (default: <repo>/kirmod if present, else <repo>/../Kirmod)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${KIRMOD_DIR:-}" ]; then
    if [ -d "$ROOT/kirmod/src" ]; then KIRMOD_DIR="$ROOT/kirmod"; else KIRMOD_DIR="$ROOT/../Kirmod"; fi
fi
test -f "$KIRMOD_DIR/src/net/minecraft/src/mod_Kirmod.java" || { echo "Kirmod sources not found in $KIRMOD_DIR (set KIRMOD_DIR)" >&2; exit 1; }
KIRMOD_DIR="$(cd "$KIRMOD_DIR" && pwd)"

OUT="$ROOT/build/kirmod"
rm -rf "$OUT"
mkdir -p "$OUT/libs" "$OUT/patches"
cp -R "$KIRMOD_DIR/src/." "$OUT/libs/"
cp -R "$KIRMOD_DIR/ci/libs/." "$OUT/libs/"
cp -R "$KIRMOD_DIR/ci/patches/client/." "$OUT/patches/"

echo "Kirmod ($KIRMOD_DIR) laid out in $OUT"
