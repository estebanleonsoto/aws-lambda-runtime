(ns thoughtsforge.aws-lambda-runtime.impl.context-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [thoughtsforge.aws-lambda-runtime.impl.context :as context]))

(deftest from-headers-test
  (testing "extracts all known context fields"
    (is (match? {:request-id       "req-abc-123"
                 :deadline-ms      1741651200000
                 :function-arn     "arn:aws:lambda:us-east-1:123:function:my-fn"
                 :trace-id         "Root=1-abc;Parent=def;Sampled=1"
                 :client-context   "eyJhIjoxfQ=="
                 :cognito-identity "{\"identityId\":\"us-east-1:abc\"}"}
                (context/from-headers
                 {"lambda-runtime-aws-request-id"       "req-abc-123"
                  "lambda-runtime-deadline-ms"          "1741651200000"
                  "lambda-runtime-invoked-function-arn" "arn:aws:lambda:us-east-1:123:function:my-fn"
                  "lambda-runtime-trace-id"             "Root=1-abc;Parent=def;Sampled=1"
                  "lambda-runtime-client-context"       "eyJhIjoxfQ=="
                  "lambda-runtime-cognito-identity"     "{\"identityId\":\"us-east-1:abc\"}"}))))

  (testing "returns nil for absent optional headers"
    (is (match? {:request-id       nil
                 :deadline-ms      nil
                 :function-arn     nil
                 :trace-id         nil
                 :client-context   nil
                 :cognito-identity nil}
                (context/from-headers {}))))

  (testing "parses deadline-ms as a long"
    (let [ctx (context/from-headers {"lambda-runtime-deadline-ms" "9999999999999"})]
      (is (= 9999999999999 (:deadline-ms ctx)))
      (is (int? (:deadline-ms ctx)))))

  (testing "ignores unrelated headers"
    (let [ctx (context/from-headers {"content-type"                  "application/json"
                                     "lambda-runtime-aws-request-id" "req-xyz"})]
      (is (= "req-xyz" (:request-id ctx))))))