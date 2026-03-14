#!/usr/bin/env bash
set -euo pipefail

IMAGE="{{artifact/id}}-builder"
OUT="target"

echo "==> Building native image via Docker (Amazon Linux 2023 + GraalVM 21)..."
docker build -f Dockerfile.build -t "$IMAGE" .

echo ""
echo "==> Extracting bootstrap binary..."
mkdir -p "$OUT"
cid=$(docker create "$IMAGE")
docker cp "$cid:/bootstrap" "$OUT/bootstrap"
docker rm "$cid" > /dev/null
chmod +x "$OUT/bootstrap"

echo "==> Creating function.zip..."
(cd "$OUT" && zip -j function.zip bootstrap)

echo ""
echo "Done."
echo "  Binary : $(du -h $OUT/bootstrap | cut -f1)"
echo "  Zip    : $(du -h $OUT/function.zip | cut -f1)"
echo ""
echo "Deploy with:"
echo "  aws lambda update-function-code \\"
echo "    --function-name <your-function> \\"
echo "    --zip-file fileb://$OUT/function.zip"