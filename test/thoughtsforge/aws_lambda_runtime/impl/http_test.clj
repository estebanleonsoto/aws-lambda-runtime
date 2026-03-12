(ns thoughtsforge.aws-lambda-runtime.impl.http-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [thoughtsforge.aws-lambda-runtime.impl.http :as http])
  (:import [com.sun.net.httpserver HttpServer HttpExchange]
           [java.net InetSocketAddress ServerSocket]
           [java.nio.charset StandardCharsets]))

(defn- free-port []
  (with-open [s (ServerSocket. 0)]
    (.getLocalPort s)))

(defn- read-body [^HttpExchange ex]
  (String. (.readAllBytes (.getRequestBody ex)) StandardCharsets/UTF_8))

(defn- send-response! [^HttpExchange ex status body-str extra-headers]
  (let [body-bytes (.getBytes body-str StandardCharsets/UTF_8)
        headers    (.getResponseHeaders ex)]
    (doseq [[k v] extra-headers]
      (.add headers k v))
    (.sendResponseHeaders ex status (count body-bytes))
    (doto (.getResponseBody ex)
      (.write body-bytes)
      (.close))))

(defn- make-server [port handler-fn]
  (doto (HttpServer/create (InetSocketAddress. port) 0)
    (.createContext "/"
                    (reify com.sun.net.httpserver.HttpHandler
                      (handle [_ exchange] (handler-fn exchange))))
    (.start)))

(deftest get!-test
  (testing "returns status, headers, and body"
    (let [port   (free-port)
          server (make-server port
                              (fn [ex]
                                (send-response! ex 200 "hello"
                                                {"X-Custom-Header" "my-value"})))]
      (try
        (let [resp (http/get! (str "http://localhost:" port "/test"))]
          (is (= 200 (:status resp)))
          (is (= "hello" (:body resp)))
          (is (= "my-value" (get (:headers resp) "x-custom-header"))))
        (finally
          (.stop server 0))))))

(deftest post!-test
  (testing "sends body and Content-Type, returns status and body"
    (let [port          (free-port)
          received-body (atom nil)
          received-ct   (atom nil)
          server        (make-server port
                                     (fn [^HttpExchange ex]
                                       (reset! received-body (read-body ex))
                                       (reset! received-ct   (-> ex .getRequestHeaders
                                                                 (.getFirst "Content-Type")))
                                       (send-response! ex 202 "accepted" {})))]
      (try
        (let [resp (http/post! (str "http://localhost:" port "/submit") "{\"key\":\"val\"}")]
          (is (= 202 (:status resp)))
          (is (= "accepted" (:body resp)))
          (is (= "{\"key\":\"val\"}" @received-body))
          (is (= "application/json" @received-ct)))
        (finally
          (.stop server 0))))))