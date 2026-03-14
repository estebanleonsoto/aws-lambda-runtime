# {{artifact/id}}

AWS Lambda function built with [com.thoughtsforge/aws-lambda-runtime](https://github.com/estebanleonsoto/aws-lambda-runtime).

## Handler

Edit `src/{{top/file}}/{{main/file}}.clj`. The entry point is:

```clojure
(defn handle [event context]
  ...)
```

`event` is the Lambda payload as a keywordized map. `context` contains `:request-id`, `:deadline-ms`, `:function-arn`, `:trace-id`, `:client-context`, `:cognito-identity`.

## Development

```bash
# Start REPL
clj -M:dev

# Run tests
clj -M:test -m kaocha.runner
```

## Build & Deploy

**1. Build the native binary (requires Docker):**

```bash
chmod +x build.sh
./build.sh
```

This produces `target/function.zip` with the `bootstrap` native binary.

**2. Create the Lambda function** (first time):

```bash
aws lambda create-function \
  --function-name {{artifact/id}} \
  --runtime provided.al2023 \
  --architectures x86_64 \
  --role arn:aws:iam::<account-id>:role/<execution-role> \
  --handler ignored \
  --zip-file fileb://target/function.zip
```

**3. Update the code** (subsequent deploys):

```bash
aws lambda update-function-code \
  --function-name {{artifact/id}} \
  --zip-file fileb://target/function.zip
```

**4. Invoke:**

```bash
aws lambda invoke \
  --function-name {{artifact/id}} \
  --payload '{"name":"world"}' \
  response.json && cat response.json
```

## GraalVM reflection

GraalVM native-image performs a closed-world analysis at build time: it only includes code it can statically prove is reachable. Any class loaded or inspected at runtime via reflection — without a prior declaration — will cause a `ClassNotFoundException` or `NoSuchMethodException` in the native binary, even if it works fine on the JVM.

Register any such classes in `resources/reflect-config.json`.

### Common cases that require reflection config

**1. Clojure namespaces loaded dynamically**
Your handler namespace init class is already registered in `reflect-config.json`. If you `require` additional namespaces at runtime (e.g., inside a conditional or via `requiring-resolve`), their `__init` classes must also be registered.

**2. Java libraries that use `Class.forName`**
Many Java libraries discover implementations at runtime: Jackson (already covered), JDBC drivers, logging backends. Any library using `Class.forName(...)` or the `ServiceLoader` SPI pattern needs its implementation classes registered.

**3. AWS SDK v2**
This is the most common real-world case. The AWS SDK v2 uses reflection to instantiate HTTP clients, credentials providers, and request marshallers. If you add a dependency like `software.amazon.awssdk/dynamodb`, calls that work on the JVM will fail at runtime in the native binary.

For example, creating a DynamoDB client:

```clojure
(ns my-lambda.core
  (:import [software.amazon.awssdk.services.dynamodb DynamoDbClient]
           [software.amazon.awssdk.regions Region]))

(def ddb (-> (DynamoDbClient/builder)
             (.region Region/US_EAST_1)
             (.build)))
```

This silently relies on reflection to load the default HTTP client (`UrlConnectionHttpClient`) and credentials provider chain. You would need entries like these in `reflect-config.json`:

```json
[
  {
    "name": "software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient$DefaultBuilder",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider",
    "allDeclaredConstructors": true,
    "allPublicMethods": true
  }
]
```

The exact set of classes varies by SDK service and configuration. The reliable way to discover them all is to use the GraalVM tracing agent (see below).

**4. `defmulti` / `defprotocol` with external implementations**
If dispatch or protocol extension happens at runtime against classes the compiler cannot see statically, those classes may need to be registered.

### Discovering missing reflection config with the tracing agent

Run your uberjar under the standard JVM with the native-image tracing agent attached. The agent records every reflective access and writes the config for you:

```bash
# Build the uberjar first
clj -T:build uber

# Run with the tracing agent (simulates a Lambda invocation)
java \
  -agentlib:native-image-agent=config-output-dir=graalvm-config \
  -jar target/bootstrap.jar
```

Exercise every code path (different event shapes, error paths). The agent writes `graalvm-config/reflect-config.json` (and others). Merge its output into `resources/reflect-config.json`, then rebuild with `./build.sh`.

> The tracing agent is included with GraalVM. If you are not running GraalVM locally, run the above inside the Docker builder image:
> ```bash
> docker run --rm -v "$PWD":/build -w /build \
>   $(docker build -q -f Dockerfile.build --target builder .) \
>   java -agentlib:native-image-agent=config-output-dir=graalvm-config \
>        -jar target/bootstrap.jar
> ```