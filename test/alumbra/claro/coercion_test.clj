(ns alumbra.claro.coercion-test
  (:require [clojure.test.check
             [properties :as prop]
             [generators :as gen]
             [clojure-test :refer [defspec]]]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Test Schema

(def schema
  (fix/schema
    "enum Emotion { HAPPY HAPPIER THE_HAPPIEST }
     type Combined {
       id: ID!
       int: Int!
       string: String!
       float: Float!
       bool: Boolean!
       enum: Emotion!
     }
     type QueryRoot {
       combine(
         id: ID!,
         int: Int!,
         string: String!,
         float: Float!
         bool: Boolean!
         enum: Emotion!
       ): Combined!
     }
     schema { query: QueryRoot }"))

;; ## Test Resolvables

(defrecord Combine [id int string float bool enum]
  data/Resolvable
  (resolve! [this _]
    (into {} this)))

(def QueryRoot
  {:combine    (map->Combine {})})

;; ## Generators

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

;; # #Tests

(defspec t-coercion 100
  (let [execute! (fix/execute-fn
                   {:schema schema
                    :query  QueryRoot})]
    (prop/for-all
      [id     graphql-id
       int    gen/int
       string graphql-string
       float  graphql-float
       bool   gen/boolean
       enum   (gen/elements ["HAPPY" "HAPPIER" "THE_HAPPIEST"])]
      (let [result (execute!
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
           (get-in result [:data "combine"]))))))
