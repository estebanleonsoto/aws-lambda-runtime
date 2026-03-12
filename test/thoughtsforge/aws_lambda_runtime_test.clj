(ns thoughtsforge.aws-lambda-runtime-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [thoughtsforge.aws-lambda-runtime :as runtime]
            [thoughtsforge.aws-lambda-runtime.impl.http :as http]))

(def ^:private request-id "test-req-001")

(defn- fake-next-response [event-payload]
  {:status  200
   :headers {"lambda-runtime-aws-request-id"       request-id
             "lambda-runtime-deadline-ms"          "9999999999999"
             "lambda-runtime-invoked-function-arn" "arn:aws:lambda:us-east-1:123:function:test"}
   :body    (json/generate-string event-payload)})

(deftest successful-invocation-test
  (testing "calls handler with parsed event+context, posts JSON response"
    (let [posted      (atom nil) get-calls   (atom 0)
          handler     (fn [event ctx]
                        {:echo-name (:name event)
                         :req-id    (:request-id ctx)})]
      (with-redefs [http/get!  (fn [_url]
                                 ;; serve one event, then stop the loop on the next poll
                                 (if (zero? @get-calls)
                                   (do (swap! get-calls inc)
                                       (fake-next-response {:name "world"}))
                                   (throw (ex-info "stop" {}))))
                    http/post! (fn [_url body] (reset! posted body))]
        (try (runtime/start! handler) (catch Exception _)))
      (is (= {:echo-name "world" :req-id request-id}
             (json/parse-string @posted true))))))

(deftest handler-exception-test
  (testing "handler exception is caught and posted as invocation error"
    (let [posted  (atom nil)
          handler (fn [_event _ctx] (throw (ex-info "boom" {})))]
      (with-redefs [http/get!  (fn [_url] (fake-next-response {}))
                    http/post! (fn [_url body]
                                 (reset! posted body)
                                 (throw (ex-info "stop" {})))]
        (try (runtime/start! handler) (catch Exception _)))
      (let [err (json/parse-string @posted true)]
        (is (= "boom" (:errorMessage err)))
        (is (string? (:errorType err)))))))

(deftest post-init-error-test
  (testing "post-init-error! sends errorMessage and errorType"
    (let [posted (atom nil)]
      (with-redefs [http/post! (fn [_url body] (reset! posted body))]
        (runtime/post-init-error! (RuntimeException. "init failed")))
      (let [err (json/parse-string @posted true)]
        (is (= "init failed" (:errorMessage err)))
        (is (= "java.lang.RuntimeException" (:errorType err)))))))