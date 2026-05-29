(ns thoughtsforge.aws-sse-lambda-runtime
  "AWS Lambda custom runtime for SSE (Server-Sent Events) response streaming.

  Requires a Lambda Function URL configured with InvokeMode=RESPONSE_STREAM.

  Usage:

    (ns my-lambda.core
      (:require [thoughtsforge.aws-sse-lambda-runtime :as runtime]
                [cheshire.core :as json])
      (:gen-class))

    (defn handle [event context write!]
      (write! {:data (json/generate-string {:status \"processing\"})})
      ;; ... do work ...
      (write! {:data (json/generate-string {:result \"done\"})}))

    (defn -main [& _]
      (runtime/start! handle))

  The handler receives:
    event   — keywordized map of the Lambda input payload
    context — map with :request-id :deadline-ms :function-arn
                        :trace-id :client-context :cognito-identity
    write!  — fn that encodes and flushes one SSE event frame.
              Accepts a map {:data str, :event str (optional), :id str (optional)}
              or a plain string (used as the data field).

  start! blocks indefinitely. Lambda controls the process lifecycle."
  (:require [thoughtsforge.aws-lambda-runtime.impl.http    :as http]
            [thoughtsforge.aws-lambda-runtime.impl.context :as context]
            [cheshire.core :as json])
  (:import [java.io PipedInputStream PipedOutputStream OutputStreamWriter]
           [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]))

(def ^:private client
  (delay (HttpClient/newHttpClient)))

(defn- api-url [path]
  (str "http://" (System/getenv "AWS_LAMBDA_RUNTIME_API") "/2018-06-01" path))

(defn- next-invocation! []
  (let [{:keys [headers body]} (http/get! (api-url "/runtime/invocation/next"))
        request-id (get headers "lambda-runtime-aws-request-id")]
    {:request-id request-id
     :context    (context/from-headers headers)
     :event      (json/parse-string body true)}))

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

(defn- sse-frame [m]
  (if (string? m)
    (str "data: " m "\n\n")
    (let [sb (StringBuilder.)]
      (when-let [id (:id m)] (.append sb (str "id: " id "\n")))
      (when-let [ev (:event m)] (.append sb (str "event: " ev "\n")))
      (.append sb (str "data: " (:data m) "\n"))
      (.append sb "\n")
      (.toString sb))))

(defn- stream-response! [request-id handler event context]
  (let [pipe-out  (PipedOutputStream.)
        ;; 64 KiB pipe buffer — large enough to avoid stalling on typical SSE events
        pipe-in   (PipedInputStream. pipe-out 65536)
        writer    (OutputStreamWriter. pipe-out "UTF-8")
        write!    (fn [m]
                    (.write writer (sse-frame m))
                    (.flush writer))
        url       (api-url (str "/runtime/invocation/" request-id "/response"))
        request   (-> (HttpRequest/newBuilder (URI/create url))
                      (.POST (HttpRequest$BodyPublishers/ofInputStream
                              (reify java.util.function.Supplier (get [_] pipe-in))))
                      (.header "Content-Type" "text/event-stream")
                      (.build))
        ;; Start the HTTP POST before calling the handler so the Lambda Runtime API
        ;; begins reading the stream while the handler is writing to it.
        http-fut  (future (.send ^HttpClient @client request (HttpResponse$BodyHandlers/ofString)))]
    (try
      (handler event context write!)
      (finally
        ;; Closing the writer flushes and closes pipe-out, sending EOF to pipe-in,
        ;; which signals the body publisher that the stream is complete.
        (.close writer)))
    ;; Wait for the HTTP response only on the happy path. If the handler threw,
    ;; the exception propagates here and http-fut resolves in the background
    ;; (pipe-out is already closed so the request will complete shortly).
    @http-fut))

(defn start!
  "Starts the SSE Lambda runtime polling loop with the given streaming handler.
  handler must be a fn of [event context write!].
  Blocks indefinitely — Lambda controls the process lifecycle."
  [handler]
  (loop []
    (let [{:keys [request-id context event]} (next-invocation!)]
      (try
        (stream-response! request-id handler event context)
        (catch Throwable e
          (post-invocation-error! request-id e))))
    (recur)))