<p align="center">
  <img src="https://raw.githubusercontent.com/estebanleonsoto/aws-lambda-runtime/main/thoughtsforge-lambda.svg"
       alt="Thoughtsforge aws-lambda-runtime" width="280"/>
</p>

<h1 align="center">aws-lambda-runtime</h1>

<p align="center">
  <a href="https://clojars.org/com.thoughtsforge/aws-lambda-runtime">
    <img src="https://img.shields.io/clojars/v/com.thoughtsforge/aws-lambda-runtime.svg" alt="Clojars Project"/>
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"/>
  </a>
</p>

A lightweight Clojure library for building [AWS Lambda custom runtimes](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html). Implements the Lambda Runtime API polling loop using only JDK built-ins — no extra HTTP dependencies, GraalVM native-image friendly.

## Installation

```clojure
;; deps.edn
{:deps {com.thoughtsforge/aws-lambda-runtime {:mvn/version "0.01.000"}}}
```

## Usage

Write your handler and pass it to `start!` from your `-main`:

```clojure
(ns my-lambda.core
  (:require [thoughtsforge.aws-lambda-runtime :as runtime])
  (:gen-class))

(defn handle [event context]
  {:statusCode 200
   :body       (str "Hello, " (get event :name "world"))})

(defn -main [& _]
  (try
    (runtime/start! handle)
    (catch Throwable e
      (runtime/post-init-error! e)
      (System/exit 1))))
```

### Handler signature

```clojure
(defn handle [event context] ...)
```

| Arg | Type | Description |
|---|---|---|
| `event` | keywordized map | The Lambda input payload |
| `context` | map | Runtime metadata (see below) |

**Context keys:** `:request-id` `:deadline-ms` `:function-arn` `:trace-id` `:client-context` `:cognito-identity`

### Middleware

Because the handler is just a function, Ring-style middleware composition works naturally:

```clojure
(defn -main [& _]
  (runtime/start! (-> handle
                      wrap-logging
                      wrap-coerce-response)))
```

## Building a native binary with GraalVM

This library is designed to work with [GraalVM native-image](https://www.graalvm.org/latest/reference-manual/native-image/) for cold-start times under 300ms. AOT-compile your handler namespace and produce a `bootstrap` binary:

```bash
# 1. Build an uberjar
clj -T:build uber

# 2. Compile to native (requires GraalVM + native-image)
native-image \
  -jar target/my-lambda.jar \
  -o bootstrap \
  --no-fallback \
  --initialize-at-build-time
```

Deploy the `bootstrap` binary as a Lambda function with the `provided.al2023` runtime.

## API

| Fn | Description |
|---|---|
| `(runtime/start! handler-fn)` | Starts the polling loop. Blocks indefinitely. |
| `(runtime/post-init-error! throwable)` | Reports a fatal init error to Lambda before exiting. |

## License

MIT © Thoughtsforge