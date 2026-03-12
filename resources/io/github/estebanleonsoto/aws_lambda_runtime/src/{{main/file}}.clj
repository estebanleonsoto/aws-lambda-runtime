(ns {{top/ns}}.{{main/ns}}
  (:require [thoughtsforge.aws-lambda-runtime :as runtime])
  (:gen-class))

(defn handle [event context]
  {:statusCode 200
   :body       "Hello from Lambda!"})

(defn -main [& _]
  (try
    (runtime/start! handle)
    (catch Throwable e
      (runtime/post-init-error! e)
      (System/exit 1))))