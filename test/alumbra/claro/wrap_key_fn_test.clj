(ns alumbra.claro.wrap-key-fn-test
  (:require [clojure.test :refer :all]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Fixtures

(def schema
  (fix/schema
    "type Result {
       column1: Int!
       column2: Int!
       otherColumn: Int!
     }
     type QueryRoot { result: Result }
     schema { query: QueryRoot }"))

;; ## Tests

(deftest t-default-key-fn
  (let [execute! (fix/execute-fn
                   {:schema schema
                    :query {:result {:column-1 1
                                     :column-2 2
                                     :other-column 3}}})]
    (is (= {:data
            {"result"
             {"column1" 1
              "column2" 2
              "otherColumn" 3}}}
           (execute!
             "{ result { column1, column2, otherColumn } }")))))

(deftest t-wrap-key-fn
  (let [execute! (fix/execute-fn
                   {:schema schema
                    :wrap-key-fn
                    (fn [f]
                      (fn [k]
                        (if (.startsWith k "column")
                          (keyword k)
                          (f k))))
                    :query
                    {:result {:column1 1
                              :column2 2
                              :other-column 3}}})]
    (is (= {:data
            {"result"
             {"column1" 1
              "column2" 2
              "otherColumn" 3}}}
           (execute!
             "{ result { column1, column2, otherColumn } }")))))
