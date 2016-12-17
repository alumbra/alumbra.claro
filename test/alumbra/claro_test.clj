(ns alumbra.claro-test
  (:require [clojure.test.check
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [alumbra.generators :as alumbra-gen]
            [alumbra.claro :as claro]
            [alumbra.claro.fixtures :as fix]))

;; ## Executor

(def execute!
  (let [f (claro/make-executor
          {:schema fix/schema
           :query  fix/QueryRoot})]
    (fn [query & [context]]
      (->> query
           (fix/parse)
           (fix/canonicalize)
           (f context)))))

;; ## Tests

(def gen-operation
  (alumbra-gen/operation fix/schema))

(defspec t-projection-generation 500
  (prop/for-all
    [operation-string (gen-operation :query)]
    (map? (execute! operation-string))))
