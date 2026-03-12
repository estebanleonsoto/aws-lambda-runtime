(ns thoughtsforge.aws-lambda-runtime.impl.context
  "Builds a Lambda context map from Runtime API invocation response headers.")

(defn from-headers
  "Extracts Lambda context from lowercase response headers."
  [headers]
  {:request-id       (get headers "lambda-runtime-aws-request-id")
   :deadline-ms      (some-> (get headers "lambda-runtime-deadline-ms") parse-long)
   :function-arn     (get headers "lambda-runtime-invoked-function-arn")
   :trace-id         (get headers "lambda-runtime-trace-id")
   :client-context   (get headers "lambda-runtime-client-context")
   :cognito-identity (get headers "lambda-runtime-cognito-identity")})