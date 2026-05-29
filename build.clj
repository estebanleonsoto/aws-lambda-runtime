(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.thoughtsforge/aws-lambda-runtime)
(def version "0.01.002")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     @basis
                :src-dirs  ["src"]
                :scm       {:url                 "https://github.com/estebanleonsoto/aws-lambda-runtime"
                            :connection          "scm:git:git://github.com/estebanleonsoto/aws-lambda-runtime.git"
                            :developerConnection "scm:git:ssh://git@github.com/estebanleonsoto/aws-lambda-runtime.git"
                            :tag                 (str "v" version)}
                :pom-data  [[:description "A lightweight Clojure library for building AWS Lambda custom runtimes. GraalVM native-image friendly."]
                            [:url "https://github.com/estebanleonsoto/aws-lambda-runtime"]
                            [:licenses
                             [:license
                              [:name "MIT License"]
                              [:url "https://opensource.org/licenses/MIT"]]]]})
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  (format "target/%s-%s.jar" (name lib) version)}))

(defn install [_]
  (jar nil)
  (b/install {:class-dir class-dir
              :lib       lib
              :version   version
              :basis     @basis
              :jar-file  (format "target/%s-%s.jar" (name lib) version)}))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact  (format "target/%s-%s.jar" (name lib) version)
              :pom-file  (b/pom-path {:lib lib :class-dir class-dir})}))