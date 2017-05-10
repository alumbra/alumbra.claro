(ns alumbra.claro.default-values-test
  (:require [clojure.test :refer :all]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Fixtures

(def schema
  (fix/schema
    "type QueryRoot { inc(value: Int = 0): Int! }
     schema { query: QueryRoot }"))

(defrecord Inc [value]
  data/Resolvable
  (resolve! [_ _]
    (inc value)))

(def execute!
  (fix/execute-fn
    {:schema schema
     :query  {:inc (map->Inc {})}}))

(defn- result=
  [expected result]
  (= expected (get-in result [:data "inc"])))

;; ## Tests

(deftest t-argument-default-values
  (is (result= 1 (execute! "{ inc(value: 0) }")))
  (is (result= 2 (execute! "{ inc(value: 1) }")))
  (is (result= 1 (execute! "{ inc }"))))

(deftest t-variable-default-values
  (let [query-inc (partial execute!
                           "query ($value: Int = 0) {
                              inc(value: $value)
                            }")]
    (is (result= 1 (query-inc {"value" 0})))
    (is (result= 2 (query-inc {"value" 1})))
    (is (result= 1 (query-inc)))))
