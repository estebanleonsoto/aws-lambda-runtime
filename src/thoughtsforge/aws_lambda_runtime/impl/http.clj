(ns thoughtsforge.aws-lambda-runtime.impl.http
  "Thin HTTP wrapper over java.net.http.HttpClient.
  Uses the JDK built-in client — no extra dependencies, GraalVM-friendly."
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpHeaders HttpRequest
            HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers]))

(def ^:private client
  (delay (HttpClient/newHttpClient)))

(defn- parse-headers
  [^HttpHeaders http-headers]
  (reduce (fn [acc [k vs]] (assoc acc (str/lower-case k) (first vs)))
          {}
          (.map http-headers)))

(defn get!
  "Blocking GET. Returns {:status int :headers map :body string}."
  [url]
  (let [request  (-> (HttpRequest/newBuilder (URI/create url))
                     (.GET)
                     (.build))
        response ^HttpResponse (.send ^HttpClient @client request
                                      (HttpResponse$BodyHandlers/ofString))]
    {:status  (.statusCode response)
     :headers (parse-headers (.headers response))
     :body    (.body response)}))

(defn post!
  "Blocking POST with a JSON string body. Returns {:status int :body string}."
  [url body]
  (let [request  (-> (HttpRequest/newBuilder (URI/create url))
                     (.POST (HttpRequest$BodyPublishers/ofString body))
                     (.header "Content-Type" "application/json")
                     (.build))
        response ^HttpResponse (.send ^HttpClient @client request
                                      (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body   (.body response)}))