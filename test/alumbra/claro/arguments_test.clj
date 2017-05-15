(ns alumbra.claro.arguments-test
  (:require [clojure.test :refer :all]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Fixtures

(def schema
  (fix/schema
    "input InputObject {
       innerScalar: Int
       innerObject: InputObject
       innerList: [InputObject!]
     }
     type QueryRoot {
       query(in: InputObject): Boolean!
       queryList(in: [InputObject]): Boolean!
     }
     schema { query: QueryRoot }"))

(defrecord Q [in]
  data/Resolvable
  (resolve! [_ _]
    true))

(def execute!
  (fix/execute-fn
    {:schema schema
     :query {:query      (map->Q {})
             :query-list (map->Q {})}}))

;; ## Tests

(deftest t-null-values-within-object
  (testing "inline."
    (are [field-to-set-null]
         (= {:data {"query" true}}
            (execute!
              (format "{ query(in: {%s: null}) }"
                      field-to-set-null)))
         "innerScalar"
         "innerObject"
         "innerList"))
  (testing "via variable."
    (are [field-to-set-null]
         (= {:data {"query" true}}
            (execute!
              "query ($in: InputObject) { query(in: $in) }"
              {"in" {field-to-set-null nil}}))
         "innerScalar"
         "innerObject"
         "innerList")))

(deftest t-null-values-within-list
  (testing "inline."
    (is (= {:data {"queryList" true}}
           (execute! "{ queryList(in: [null]) }"))))
  (testing "via variable"
    (is (= {:data {"queryList" true}}
           (execute!
             "query($in: InputObject) { queryList(in: [$in]) }"
             {"in" nil})))
    (is (= {:data {"queryList" true}}
           (execute!
             "query($in: [InputObject]) { queryList(in: $in) }"
             {"in" [nil]})))))
