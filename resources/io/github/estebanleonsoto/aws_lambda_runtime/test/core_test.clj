(ns {{top/ns}}.{{main/ns}}.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [{{top/ns}}.{{main/ns}}.core :as core]))

(deftest handle-test
  (testing "returns a successful response"
    (let [response (core/handle {} {})]
      (is (= 200 (:statusCode response))))))