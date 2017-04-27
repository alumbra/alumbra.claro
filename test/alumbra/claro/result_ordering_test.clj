(ns alumbra.claro.result-ordering-test
  (:require [clojure.test.check
             [properties :as prop]
             [generators :as gen]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [claro.data :as data]
            [clojure.string :as string]
            [alumbra.claro.fixtures :as fix]))

;; ## Test Schema

(def schema
  (fix/schema
    "type QueryRoot {
       x: Int!
       y: Int!
       z: Int!
     }
     schema { query: QueryRoot }"))

;; ## Tests

(defspec t-result-order-matches-query 50
  (let [execute! (fix/execute-fn
                   {:schema schema
                    :query {:x 0, :y 1, :z 2}})]
    (prop/for-all
      [ordered-fields (-> (gen/elements ["x" "y" "z"])
                          (gen/vector-distinct
                            {:min-elements 1, :max-elements 3}))]
      (let [query (format "{%s}" (string/join " " ordered-fields))
            result (execute! query)]
        (is (= ordered-fields (keys (:data result))))))))
