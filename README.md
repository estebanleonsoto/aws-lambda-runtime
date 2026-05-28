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

## Quickstart

Scaffold a new Lambda project using the built-in [deps-new](https://github.com/seancorfield/deps-new) template:

```bash
clj -Tnew create \
  :template io.github.estebanleonsoto/aws-lambda-runtime \
  :name myorg/my-lambda
```

This generates a ready-to-deploy project with handler, tests, uberjar build, and GraalVM config included. See the [Getting Started guide](docs/getting-started.md) for the full walkthrough.

## Installation

```clojure
;; deps.edn
{:deps {com.thoughtsforge/aws-lambda-runtime {:mvn/version "0.01.001"}}}
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

## SSE Response Streaming

For Lambda functions that stream responses via [Server-Sent Events](https://docs.aws.amazon.com/lambda/latest/dg/configuration-response-streaming.html), use the `aws-sse-lambda-runtime` namespace. This requires a **Lambda Function URL** configured with `InvokeMode=RESPONSE_STREAM`.

### Installation

Same dependency — the SSE runtime is bundled in the same jar:

```clojure
{:deps {com.thoughtsforge/aws-lambda-runtime {:mvn/version "0.01.001"}}}
```

### Usage

```clojure
(ns my-lambda.core
  (:require [thoughtsforge.aws-sse-lambda-runtime :as runtime]
            [cheshire.core :as json])
  (:gen-class))

(defn handle [event context write!]
  (write! {:data (json/generate-string {:status "processing"})})
  ;; ... do work ...
  (write! {:data (json/generate-string {:result "done"})}))

(defn -main [& _]
  (try
    (runtime/start! handle)
    (catch Throwable e
      (runtime/post-init-error! e)
      (System/exit 1))))
```

### Handler signature

```clojure
(defn handle [event context write!] ...)
```

| Arg | Type | Description |
|---|---|---|
| `event` | keywordized map | The Lambda input payload |
| `context` | map | Runtime metadata (same keys as standard runtime) |
| `write!` | fn | Encodes and flushes one SSE event frame |

**`write!` accepts:**

| Form | Behaviour |
|---|---|
| `{:data "..."}` | Required field — the event payload |
| `{:data "..." :event "mytype" :id "1"}` | Optional `event` type and `id` fields |
| `"plain string"` | Treated as the `data` field |

Each call to `write!` flushes a complete SSE frame immediately. The Lambda Runtime API begins reading the stream while your handler is writing, so the client receives events incrementally.

### SSE API

| Fn | Description |
|---|---|
| `(runtime/start! handler-fn)` | Starts the SSE polling loop. Blocks indefinitely. |
| `(runtime/post-init-error! throwable)` | Reports a fatal init error to Lambda before exiting. |

## GraalVM

Designed for [GraalVM native-image](https://www.graalvm.org/latest/reference-manual/native-image/) — cold starts under 300ms. See the [Getting Started guide](docs/getting-started.md) for the full build and deploy instructions.

## API

| Fn | Description |
|---|---|
| `(runtime/start! handler-fn)` | Starts the polling loop. Blocks indefinitely. |
| `(runtime/post-init-error! throwable)` | Reports a fatal init error to Lambda before exiting. |

## License

MIT © Thoughtsforge