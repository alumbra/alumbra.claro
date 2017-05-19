(ns alumbra.claro.coercion-test
  (:require [clojure.test.check
             [properties :as prop]
             [generators :as gen]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [camel-snake-kebab.core :as csk]
            [claro.data :as data]
            [alumbra.claro.fixtures :as fix]))

;; ## Test Schema

(def schema
  (fix/schema
    "enum Emotion { HAPPY HAPPIER THE_HAPPIEST }
     interface Interface { v: Int! }
     type Object implements Interface { v: Int! }
     union U = Object
     type QueryRoot {
       asId(v: ID!): ID
       asInt(v: Int!): Int
       asString(v: String!): String
       asFloat(v: Float!): Float
       asBool(v: Boolean!): Boolean
       asEnum(v: Emotion!): Emotion
       asNonNull(v: ID!): ID!
       asNonNullList: [Object]!
       asNonNullObject: Object!
       asNonNullUnion: U!
       asNonNullInterface: Interface!
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

(defspec t-custom-scalar-coercion 100
  (let [encode-id #(str "id:" %)
        decode-id #(let [s (str %)]
                     (if (.startsWith s "id:")
                       (subs s 3)
                       s))
        input-valid? #(not (.startsWith % "id:"))]
    (prop/for-all
      [{:keys [field query-field value]} coerceable-id]
      (let [execute! (fix/execute-fn
                       {:schema schema
                        :query  {field (->Identity nil input-valid?)}
                        :scalars {"ID" {:encode encode-id
                                        :decode decode-id}}})
            id (str "id:" value)
            result (-> (format "{ result: %s (v: %s) }"
                               query-field
                               (pr-str id))
                       (execute!)
                       (get-in [:data "result"]))]
        (= result id)))))

(defspec t-custom-enum-coercion 100
  (let [upgrade-happiness {"HAPPY"        :happier
                           "HAPPIER"      :the-happiest
                           "THE_HAPPIEST" :the-happiest}]
    (prop/for-all
      [{:keys [field query-field input-valid? value]} coerceable-enum]
      (let [execute! (fix/execute-fn
                       {:schema schema
                        :query  {field (->Identity nil input-valid?)}
                        :scalars {"Emotion" {:decode upgrade-happiness}}})
            result (-> (format "{ result: %s (v: %s) }"
                               query-field
                               (pr-str value))
                       (execute!)
                       (get-in [:data "result"]))
            expected-result (-> (str value)
                                (upgrade-happiness)
                                (csk/->SCREAMING_SNAKE_CASE_STRING))]
        (= result expected-result)))))

(defspec t-custom-complex-value-coercion 100
  (let [decode       #(hash-map :emotion-value %)
        encode       :emotion-value
        input-valid? #(contains? % :emotion-value)]
    (prop/for-all
      [{:keys [field query-field value output-valid?]} coerceable-enum]
      (let [execute! (fix/execute-fn
                       {:schema schema
                        :query  {field (->Identity nil input-valid?)}
                        :scalars {"Emotion" {:encode encode, :decode decode}}})
            result (-> (format "{ result: %s (v: %s) }"
                               query-field
                               (pr-str value))
                       (execute!)
                       (get-in [:data "result"]))]
        (output-valid? result)))))

(deftest t-input-coercion-exception
  (let [decode #(throw (ex-info "oops." {:value %}))
        encode str
        execute! (fix/execute-fn
                   {:schema  schema
                    :query   {:as-id (->Identity nil (constantly true))}
                    :scalars {"ID" {:encode encode, :decode decode}}})]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Could not coerce value to 'ID'"
          (execute! "{ asId(v: 10) }")))))

(deftest t-output-coercion-exception
  (let [decode str
        encode #(throw (ex-info "oops." {:value %}))
        execute! (fix/execute-fn
                   {:schema  schema
                    :query   {:as-id (->Identity nil (constantly true))}
                    :scalars {"ID" {:encode encode, :decode decode}}})
        result (is (execute! "{ asId(v: 10) }"))]
    (is (= {"asId" nil} (:data result)))
    (is (= "Could not coerce value to 'ID': \"10\""
           (-> result :errors first :message)))))

(deftest t-non-nullable-result
  (let [null-value (->Identity nil (constantly false))
        execute! (fix/execute-fn
                   {:schema schema
                    :query  {:as-non-null           null-value
                             :as-non-null-interface null-value
                             :as-non-null-list      null-value
                             :as-non-null-object    null-value
                             :as-non-null-union     null-value}})
        execute-error! (comp :message first :errors execute!)]
    (is (= "Field 'asNonNull' returned 'null' but type 'ID!' is non-nullable."
           (execute-error! "{ asNonNull(v: 10) }")))
    (is (= "Field 'asNonNullList' returned 'null' but type '[Object]!' is non-nullable."
           (execute-error! "{ asNonNullList { v } }")))
    (is (= "Field 'asNonNullObject' returned 'null' but type 'Object!' is non-nullable."
           (execute-error! "{ asNonNullObject { v } }")))
    (is (= "Field 'asNonNullUnion' returned 'null' but type 'U!' is non-nullable."
           (execute-error! "{ asNonNullUnion { __typename } }")))
    (is (= "Field 'asNonNullInterface' returned 'null' but type 'Interface!' is non-nullable."
           (execute-error! "{ asNonNullInterface { __typename } }")))))
