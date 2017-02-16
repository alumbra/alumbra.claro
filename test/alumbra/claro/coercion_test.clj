(ns alumbra.claro.coercion-test
  (:require [clojure.test.check
             [properties :as prop]
             [generators :as gen]
             [clojure-test :refer [defspec]]]
            [camel-snake-kebab.core :as csk]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Test Schema

(def schema
  (fix/schema
    "enum Emotion { HAPPY HAPPIER THE_HAPPIEST }
     type QueryRoot {
       asId(v: ID!): ID!
       asInt(v: Int!): Int!
       asString(v: String!): String!
       asFloat(v: Float!): Float!
       asBool(v: Boolean!): Boolean!
       asEnum(v: Emotion!): Emotion
     }
     schema { query: QueryRoot }"))

;; ## Test Resolvables

(defrecord Identity [v pred]
  data/Resolvable
  (resolve! [this _]
    (when (pred v)
      v)))

;; ## Generators

(defn- coerceable
  [data gen]
  (gen/fmap
    #(assoc data
            :query-field (csk/->camelCaseString (:field data))
            :value %)
    gen))

(def coerceable-id
  (coerceable
    {:field         :as-id
     :input-valid?  string?
     :output-valid? string?}
    (gen/one-of
      [gen/string-alphanumeric
       gen/pos-int])))

(def coerceable-int
  (coerceable
    {:field         :as-int
     :input-valid?  integer?
     :output-valid? integer?}
    gen/int))

(def coerceable-bool
  (coerceable
    {:field         :as-bool
     :input-valid?  boolean?
     :output-valid? boolean?}
    gen/boolean))

(def gen-float
  (gen/double* {:infinite? false, :NaN? false}))

(def coerceable-float
  (coerceable
    {:field         :as-float
     :input-valid?  float?
     :output-valid? float?}
    (gen/one-of
      [gen/int
       gen-float])))

(def coerceable-string
  (coerceable
    {:field         :as-string
     :input-valid?  string?
     :output-valid? string?}
    (gen/one-of
      [gen/string-alphanumeric
       gen/pos-int
       gen-float
       gen/boolean])))

(def coerceable-enum
  (coerceable
    {:field :as-enum
     :input-valid? #{:happy :happier :the-happiest}
     :output-valid? #{"HAPPY" "HAPPIER" "THE_HAPPIEST"}}
    (gen/elements '[HAPPY HAPPIER THE_HAPPIEST])))

;; ## Tests

(defspec t-coercion 100
  (prop/for-all
    [{:keys [field query-field input-valid? output-valid? value]}
     (gen/one-of [coerceable-id
                  coerceable-int
                  coerceable-string
                  coerceable-float
                  coerceable-bool
                  coerceable-enum])]
    (let [execute! (fix/execute-fn
                     {:schema schema
                      :query  {field (->Identity nil input-valid?)}})
          result (-> (format
                       "{ result: %s (v: %s) }"
                       query-field
                       (pr-str value))
                     (execute!)
                     (get-in [:data "result"]))]
      (output-valid? result))))
