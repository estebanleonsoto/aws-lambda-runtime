#!/usr/bin/env bash
set -euo pipefail

IMAGE="{{artifact/id}}-jvm"
ECR_REPO="${ECR_REPO:-}"
FUNCTION_NAME="${FUNCTION_NAME:-}"

echo "==> Building JVM image (Corretto 21, no native-image)..."
docker build -f Dockerfile.build-jvm -t "$IMAGE" .

echo ""
echo "Done.  Local image: $IMAGE"
echo ""

if [[ -n "$ECR_REPO" ]]; then
  TAG="$ECR_REPO:latest"
  echo "==> Tagging and pushing to $TAG ..."
  docker tag "$IMAGE" "$TAG"
  docker push "$TAG"
  echo ""
  if [[ -n "$FUNCTION_NAME" ]]; then
    echo "==> Updating Lambda function code..."
    aws lambda update-function-code \
      --function-name "$FUNCTION_NAME" \
      --image-uri "$TAG"
  else
    echo "Deploy with:"
    echo "  aws lambda update-function-code \\"
    echo "    --function-name <your-function> \\"
    echo "    --image-uri $TAG"
  fi
else
  echo "To push and deploy, set ECR_REPO and optionally FUNCTION_NAME:"
  echo "  ECR_REPO=123456789.dkr.ecr.us-east-1.amazonaws.com/{{artifact/id}} \\"
  echo "  FUNCTION_NAME=<your-function> \\"
  echo "  ./build-jvm.sh"
  echo ""
  echo "Make sure the Lambda is configured to use a container image"
  echo "with package type 'Image' (not Zip)."
fi