(ns alumbra.claro.introspection
  (:require [claro.data :as data]
            [claro.engine :as engine]
            [claro.projection :as projection]
            [clojure.string :as string]))

;; ## Resolvables

(defn- by-name
  [{:keys [::introspection]} k values]
  (map
    (fn [{:keys [name]}]
      (get-in introspection [k name]))
    values))

(defn- process-deprecated
  [include? values]
  (if-not include?
    (map #(vec (remove :is-deprecated %)) values)
    values))

(defrecord Type [name]
  data/PureResolvable
  data/Resolvable
  data/BatchedResolvable
  (resolve-batch! [_ env types]
    (by-name env :types types)))

(defrecord Directive [name]
  data/PureResolvable
  data/Resolvable
  data/BatchedResolvable
  (resolve-batch! [_ env directives]
    (by-name env :directives directives)))

(defrecord EnumValues [name include-deprecated]
  data/PureResolvable
  data/Resolvable
  data/BatchedResolvable
  (resolve-batch! [_ env enum-values]
    (->> (by-name env :enum-values enum-values)
         (process-deprecated include-deprecated))))

(defrecord Fields [name include-deprecated]
  data/PureResolvable
  data/Resolvable
  data/BatchedResolvable
  (resolve-batch! [_ env fields]
    (->> (by-name env :fields fields)
         (process-deprecated include-deprecated))))

(defrecord Schema []
  data/PureResolvable
  data/Resolvable
  data/BatchedResolvable
  (resolve-batch! [_ {:keys [::introspection]} schemas]
    (repeat (count schemas)
            (get introspection :schema))))

;; ## Introspection

;; ### Helpers

(defn- make-type-map
  [values]
  (merge
    {:__typename     "__Type"
     :name           nil
     :description    nil
     :fields         (->Fields [] nil)
     :interfaces     nil
     :possible-types nil
     :enum-values    (->EnumValues [] nil)
     :input-fields   nil
     :of-type        nil}
    values))

(defn- as-nested-type
  [nested-type-description]
  (let [{:keys [type-description non-null? type-name]}
        nested-type-description]
    (cond non-null?
          (make-type-map
            {:kind    :NON_NULL
             :of-type (as-nested-type
                       (dissoc nested-type-description :non-null?))})

          type-description
          (make-type-map
            {:kind    :LIST
             :of-type (as-nested-type type-description)})

          :else
          (->Type type-name))))

(defn- as-argument
  [argument]
  (let [{:keys [argument-name type-description]} argument]
    {:__typename    "__InputValue"
     :name          argument-name
     :description   nil
     :type          (as-nested-type type-description)
     ;; FIXME: default value for arguments
     :default-value nil}))

(defn- as-arguments
  [arguments]
  (mapv as-argument arguments))

;; ### Directives

(defn- introspect-directives
  [{:keys [directives]}]
  (->> (for [[name {:keys [directive-locations arguments]}] directives]
         (->> {:__typename  "__Directive"
               :name        name
               :description nil
               :locations   (mapv
                              (fn [location]
                                (-> (clojure.core/name location)
                                    (string/replace "-" "_")
                                    (string/upper-case)))
                              directive-locations)
               :args        (as-arguments (vals arguments))}
              (vector name)))
       (into {})))

;; ### Fields

(defn- introspect-fields
  [{:keys [types interfaces union-types]}]
  (->> (for [[name {:keys [fields]}] (merge types interfaces union-types)]
         (->> (vals (dissoc fields "__typename"))
              (map
                (fn [{:keys [field-name arguments type-description]}]
                  {:__typename         "__Field"
                   :name               field-name
                   :description        nil
                   :args               (as-arguments (vals arguments))
                   :type               (as-nested-type type-description)
                   :is-deprecated      false
                   :deprecation-reason nil}))
              (sort-by :name)
              (vector name)))
       (into {})))

;; ### Enum Values

(defn- introspect-enum-values
  [{:keys [enums]}]
  (->> (for [[name {:keys [enum-values]}] enums]
         (->> (for [v enum-values]
                {:__typename         "__EnumValue"
                 :name               v
                 :description        nil
                 :is-deprecated      false
                 :deprecation-reason nil})
              (vector name)))
       (into {})))

;; ### Types

(defn as-interface-type
  [schema name]
  (let [{:keys [fields implemented-by]} (get-in schema [:interfaces name])]
    (make-type-map
      {:name           name
       :kind           :interface
       :fields         (->Fields name nil)
       :possible-types (mapv ->Type implemented-by)})))

(defn- as-object-type
  [schema name]
  (let [{:keys [fields implements]} (get-in schema [:types name])]
    (make-type-map
      {:name       name
       :kind       :object
       :fields     (->Fields name nil)
       :interfaces (mapv ->Type implements)})))

(defn- as-union-type
  [schema name]
  (let [{:keys [union-types]} (get-in schema [:unions name])]
    (make-type-map
      {:name           name
       :kind           :union
       :possible-types (mapv ->Type union-types)})))

(defn- as-input-type-fields
  [fields]
  (map
    (fn [{:keys [field-name type-description]}]
      {:__typename    "__InputValue"
       :name          field-name
       :description   nil
       :type          (as-nested-type type-description)
       ;; FIXME: default value for arguments
       :default-value nil})
    fields))

(defn- as-input-type
  [schema name]
  (let [{:keys [fields]} (get-in schema [:input-types name])]
    (make-type-map
      {:name         name
       :kind         :input-object
       :input-fields (as-input-type-fields (vals fields))})))

(defn- as-scalar-type
  [_ name]
  (make-type-map
    {:name name
     :kind :scalar}))

(defn- as-enum-type
  [schema name]
  (let [{:keys [enum-values]} (get-in schema [:enums name])]
    (make-type-map
      {:name        name
       :kind        :enum
       :enum-values (->EnumValues name nil)})))

(defn- introspect-types
  [{:keys [type->kind] :as schema}]
  (->> (for [[type-name kind] type->kind]
         (->> (case kind
                :type       (as-object-type schema type-name)
                :interface  (as-interface-type schema type-name)
                :union      (as-union-type schema type-name)
                :scalar     (as-scalar-type schema type-name)
                :enum       (as-enum-type schema type-name)
                :input-type (as-input-type schema type-name)
                nil)
              (vector type-name)))
       (into {})))

;; ### Schema Record

(defn- type-for-operation
  [schema k]
  (some-> schema
          (get-in [:schema-root :schema-root-types k])
          (->Type)))

(defn- introspect-schema
  [{:keys [type->kind directives] :as schema}]
  {:__typename        "__Schema"
   :types             (vec
                        (keep
                          (fn [[type-name kind]]
                            (when (not= kind :directive)
                              (->Type type-name)))
                          type->kind))
   :directives        (mapv #(->Directive %) (keys directives))
   :query-type        (type-for-operation schema "query")
   :mutation-type     (type-for-operation schema "mutation")
   :subscription-type (type-for-operation schema "subscription")})

;; ### Introspection Map

(defn- introspect
  [schema]
  {:directives  (introspect-directives schema)
   :fields      (introspect-fields schema)
   :enum-values (introspect-enum-values schema)
   :types       (introspect-types schema)
   :schema      (introspect-schema schema)})

;; ## Middleware

(defn- strip-introspection-fields
  [schema]
  (if-let [root-type (get-in schema [:schema-root :schema-root-types "query"])]
    (update-in schema [:types root-type :fields] dissoc "__schema" "__type")
    schema))

(defn wrap-introspection
  "Wrap the given engine to allow usage of [[->Schema]] and [[->Type]]
   resolvables for GraphQL schema introspection.

   `analyzed-schema` has to conform to `:alumbra/analyzed-schema`."
  [engine analyzed-schema]
  (let [schema (strip-introspection-fields analyzed-schema)
        introspection (introspect schema)]
    (->> (fn [resolver]
           (fn [env batch]
             (if (contains?
                   #{Schema Type Fields Directive EnumValues}
                   (class (first batch)))
               (resolver
                 (assoc env ::introspection introspection)
                 batch)
               (resolver env batch))))
         (engine/wrap engine))))
