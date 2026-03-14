#!/usr/bin/env bash
# Scaffolds a new Lambda function as a sibling of this one.
#
# Usage (from the monorepo root):
#   ./scripts/new-lambda.sh <lambda-name>
#
# Example:
#   ./scripts/new-lambda.sh notifications
#
# This creates lambdas/<lambda-name>/ with the full project structure.
# Move this script to the monorepo root and adjust LAMBDAS_DIR if needed.
set -euo pipefail

NAME=${1:?"Usage: $0 <lambda-name>"}
LAMBDAS_DIR="$(cd "$(dirname "$0")/.." && pwd)/.."
TARGET="$LAMBDAS_DIR/$NAME"

if [ -e "$TARGET" ]; then
  echo "Error: $TARGET already exists." >&2
  exit 1
fi

clj -Tnew create \
  :template io.github.estebanleonsoto/aws-lambda-runtime \
  :name {{top/ns}}/$NAME \
  :target-dir "$TARGET"

echo ""
echo "Lambda scaffolded at $TARGET"
echo "Next: edit $TARGET/src/{{top/file}}/${NAME//-/_}.clj"