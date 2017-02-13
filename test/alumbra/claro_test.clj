(ns alumbra.claro-test
  (:require [clojure.test.check
             [properties :as prop]
             [generators :as gen]
             [clojure-test :refer [defspec]]]
            [alumbra.generators :as alumbra-gen]
            [alumbra.claro.fixtures :as fix]))

;; ## Execution

(def gen-operation
  (alumbra-gen/operation fix/schema))

(defspec t-execution 500
  (prop/for-all
    [operation-string (gen-operation :query)]
    (map? (fix/execute! operation-string))))

;; ## Coercion

(def graphql-id
  (gen/one-of
    [gen/string-alphanumeric
     gen/pos-int]))

(def graphql-float
  (gen/one-of
    [gen/int
     (gen/double* {:infinite? false, :NaN? false})]))

(def graphql-string
  (gen/one-of
    [graphql-id
     graphql-float
     gen/boolean]))

(defspec t-coercion 100
  (prop/for-all
    [id     graphql-id
     int    gen/int
     string graphql-string
     float  graphql-float
     bool   gen/boolean
     enum   (gen/elements ["HAPPY" "HAPPIER" "THE_HAPPIEST"])]
    (let [result (fix/execute!
                   "query (
                      $id: ID!, $int: Int!, $string: String!,
                      $float: Float!, $bool: Boolean!, $enum: Emotion!
                    ) {
                      combine(
                        id: $id, int: $int, string: $string,
                        float: $float, bool: $bool, enum: $enum
                      ) {
                        id, int, string, float, bool, enum
                      }
                    }"
                   {"id"     id
                    "string" string
                    "int"    int
                    "float"  float
                    "bool"   bool
                    "enum"   enum})]
      (= {"id"         (str id)
          "int"        int
          "string"(str string)
          "float"      (double float)
          "bool"       bool
          "enum"       enum}
       (get-in result [:data "combine"])))))
