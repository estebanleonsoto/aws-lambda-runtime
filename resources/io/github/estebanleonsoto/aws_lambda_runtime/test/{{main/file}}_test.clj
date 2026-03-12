(ns {{top/ns}}.{{main/ns}}-test
  (:require [clojure.test :refer [deftest is testing]]
            [{{top/ns}}.{{main/ns}} :as core]))

(deftest handle-test
  (testing "returns a successful response"
    (let [response (core/handle {} {})]
      (is (= 200 (:statusCode response))))))