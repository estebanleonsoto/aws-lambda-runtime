# Getting Started

This guide walks through scaffolding, developing, and deploying a Clojure AWS Lambda function using `com.thoughtsforge/aws-lambda-runtime`.

## Prerequisites

- JDK 21+
- [Clojure CLI tools](https://clojure.org/guides/install_clojure)
- [GraalVM](https://www.graalvm.org/downloads/) with `native-image` (for deployment)
- AWS CLI configured with appropriate permissions

---

## 1. Install deps-new

If you don't have `deps-new` installed as a Clojure tool yet:

```bash
clj -Ttools install io.github.seancorfield/deps-new '{:as new}'
```

---

## 2. Scaffold your project

```bash
clj -Tnew create \
  :template io.github.estebanleonsoto/aws-lambda-runtime \
  :name myorg/my-lambda
```

Replace `myorg/my-lambda` with your own group and function name. This generates:

```
my-lambda/
  deps.edn          — project dependencies and aliases
  build.clj         — uberjar build script
  .nrepl.edn        — nREPL on port 7888
  .gitignore
  src/myorg/
    my_lambda.clj   — your Lambda handler (edit this)
  test/myorg/
    my_lambda_test.clj
```

---

## 3. Write your handler

Open `src/myorg/my_lambda.clj`. The generated handler is a starting point — replace it with your logic:

```clojure
(defn handle [event context]
  {:statusCode 200
   :body       (str "Hello, " (get event :name "world"))})
```

`event` is a keywordized map of the Lambda input payload. `context` provides runtime metadata:

| Key | Description |
|---|---|
| `:request-id` | Lambda request ID |
| `:deadline-ms` | Invocation deadline (epoch ms) |
| `:function-arn` | Invoked function ARN |
| `:trace-id` | X-Ray trace ID |
| `:client-context` | Mobile client context (if any) |
| `:cognito-identity` | Cognito identity (if any) |

### Middleware

Because the handler is just a function, you can compose middleware Ring-style:

```clojure
(defn -main [& _]
  (runtime/start! (-> handle
                      wrap-logging
                      wrap-coerce-response)))
```

---

## 4. Run tests

```bash
clj -M:test -m kaocha.runner
```

---

## 5. Start a REPL

```bash
clj -M:dev
```

Connects on port 7888. In IntelliJ + Cursive, enable the `:dev` alias in the run configuration and connect via nREPL.

---

## 6. Build the uberjar

```bash
clj -T:build uber
```

Produces `target/bootstrap.jar` with all classes AOT-compiled and bundled.

---

## 7. Compile to a native binary with GraalVM

Run this inside a Docker container matching the Lambda execution environment to ensure binary compatibility:

```bash
docker run --rm \
  -v "$(pwd)":/project \
  -w /project \
  ghcr.io/graalvm/native-image-community:21 \
  native-image \
    -jar target/bootstrap.jar \
    -o bootstrap \
    --no-fallback \
    --initialize-at-build-time \
    -H:+ReportExceptionStackTraces
```

This produces a `bootstrap` binary in the project root.

---

## 8. Deploy to AWS Lambda

Package and deploy using the `provided.al2023` custom runtime:

```bash
# Zip the bootstrap binary
zip function.zip bootstrap

# Create the Lambda function
aws lambda create-function \
  --function-name my-lambda \
  --runtime provided.al2023 \
  --role arn:aws:iam::YOUR_ACCOUNT:role/YOUR_ROLE \
  --handler ignored \
  --zip-file fileb://function.zip

# Or update an existing function
aws lambda update-function-code \
  --function-name my-lambda \
  --zip-file fileb://function.zip
```

> The `--handler` value is ignored for custom runtimes — your `-main` is the entry point.

---

## 9. Test the deployed function

```bash
aws lambda invoke \
  --function-name my-lambda \
  --payload '{"name": "world"}' \
  --cli-binary-format raw-in-base64-out \
  response.json && cat response.json
```

Expected output:

```json
{"statusCode": 200, "body": "Hello, world"}
```

---

## Using inside a monorepo

If your project has multiple Lambdas alongside Terraform infrastructure or shared code, you can scaffold each Lambda directly into a subdirectory using deps-new's `:target-dir` option.

From the monorepo root:

```
my-backend/
├── iac/           ← Terraform
└── lambdas/
    ├── auth/      ← first Lambda
    └── orders/    ← second Lambda
```

**Scaffold the first Lambda:**

```bash
clj -Tnew create \
  :template io.github.estebanleonsoto/aws-lambda-runtime \
  :name myorg/auth \
  :target-dir lambdas/auth
```

**Add subsequent Lambdas** using the `scripts/new-lambda.sh` helper that was generated with the first Lambda. Copy it to your monorepo root once:

```bash
cp lambdas/auth/scripts/new-lambda.sh scripts/new-lambda.sh
chmod +x scripts/new-lambda.sh
```

Then any time you need a new Lambda:

```bash
./scripts/new-lambda.sh orders
./scripts/new-lambda.sh notifications
```

Each Lambda is fully self-contained — its own `deps.edn`, `build.sh`, `Dockerfile.build`, and `reflect-config.json`. Build and deploy them independently.