(ns thoughtsforge.aws-lambda-runtime
  "AWS Lambda custom runtime for Clojure.

  Usage:

    (ns my-lambda.core
      (:require [thoughtsforge.aws-lambda-runtime :as runtime])
      (:gen-class))

    (defn handle [event context] ...)

    (defn -main [& _]
      (runtime/start! handle))

  The handler receives:
    event   — keywordized map of the Lambda input payload
    context — map with :request-id :deadline-ms :function-arn
                        :trace-id :client-context :cognito-identity

  start! blocks indefinitely. Lambda controls the process lifecycle."
  (:require [thoughtsforge.aws-lambda-runtime.impl.http    :as http]
            [thoughtsforge.aws-lambda-runtime.impl.context :as context]
            [cheshire.core :as json]))

(defn- api-url [path]
  (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01" path))

(defn- next-invocation! []
  (let [{:keys [headers body]} (http/get! (api-url "/runtime/invocation/next"))
        request-id (get headers "lambda-runtime-aws-request-id")]
    {:request-id request-id
     :context    (context/from-headers headers)
     :event      (json/parse-string body true)}))

(defn- post-response! [request-id result]
  (http/post! (api-url (str "/runtime/invocation/" request-id "/response"))
              (json/generate-string result)))

(defn- post-invocation-error! [request-id ^Throwable e]
  (http/post! (api-url (str "/runtime/invocation/" request-id "/error"))
              (json/generate-string {:errorMessage (.getMessage e)
                                     :errorType    (.getName (class e))})))

(defn post-init-error!
  "Reports a fatal initialization error to Lambda. Call before (System/exit 1)."
  [^Throwable e]
  (http/post! (api-url "/runtime/init/error")
              (json/generate-string {:errorMessage (.getMessage e)
                                     :errorType    (.getName (class e))})))

(defn start!
  "Starts the Lambda runtime polling loop with the given handler function.
  handler must be a fn of [event context] returning a JSON-serializable map.
  Blocks indefinitely — Lambda controls the process lifecycle."
  [handler]
  (loop []
    (let [{:keys [request-id context event]} (next-invocation!)]
      (try
        (post-response! request-id (handler event context))
        (catch Throwable e
          (post-invocation-error! request-id e))))
    (recur)))