(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib      (symbol "{{group/id}}" "{{artifact/id}}"))
(def version  "0.01.000")
(def class-dir "target/classes")
(def basis    (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis     @basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file "target/bootstrap.jar"
           :basis     @basis
           :main      (symbol "{{top/ns}}.{{main/ns}}.core")}))