(ns alumbra.claro.introspection
  (:require [claro.data :as data]
            [claro.engine :as engine]
            [claro.projection :as projection]
            [clojure.string :as string]))

;; TODO:
;; There is a bit of a performance gain to be had if we generate the maps for
;; the single types beforehand and just to a simple lookup within the 'Type'
;; resolvable.

(declare ->Directive ->EnumValues ->Fields ->Type as-nested-type)

;; ## Schema

(defn- type-for-operation
  [schema k]
  (some-> schema
          (get-in [:schema-root :schema-root-types k])
          (->Type)))

(defn- build-schema-record
  [{:keys [type->kind directives] :as schema}]
  {:__typename        "__Schema"
   :types             (vec
                        (keep
                          (fn [[type-name kind]]
                            (when (not= kind :directive)
                              (->Type type-name)))
                          type->kind))
   :directives        (mapv #(apply ->Directive %) directives)
   :query-type        (type-for-operation schema "query")
   :mutation-type     (type-for-operation schema "mutation")
   :subscription-type (type-for-operation schema "subscription")})

(defrecord Schema []
  data/Resolvable
  (resolve! [_ {:keys [::schema]}]
    (build-schema-record schema)))

;; ## Types

;; ### Base Map

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

;; ### Nested Types (Non-Null and List)

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

;; ### Arguments

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

;; ### Fields

(defn- as-field
  [field]
  (let[{:keys [field-name type-description arguments]} field]
    {:name               field-name
     :description        nil
     :args               (as-arguments (vals arguments))
     :type               (as-nested-type type-description)
     :is-deprecated      false
     :deprecation-reason nil}))

(defrecord Fields [fields include-deprecated]
  data/Resolvable
  (resolve! [_ _]
    (when (seq fields)
      (vec
        (keep
          (fn [{:keys [field-name] :as field}]
            (when (not= field-name "__typename")
              (as-field field)))
          fields)))))

;; ### Object Types

(defn- as-object-type
  [{:keys [::schema]} name]
  (let [{:keys [fields implements]}
        (get-in schema [:types name])]
    (make-type-map
      {:name       name
       :kind       :OBJECT
       :fields     (->Fields (vals fields) nil)
       :interfaces (mapv ->Type implements)})))

;; ### Interface Types

(defn as-interface-type
  [{:keys [::schema]} name]
  (let [{:keys [fields implemented-by]}
        (get-in schema [:interfaces name])]
    (make-type-map
      {:name           name
       :kind           :INTERFACE
       :fields         (->Fields (vals fields) nil)
       :possible-types (mapv ->Type implemented-by)})))

;; ### Union Types

(defn- as-union-type
  [{:keys [::schema]} name]
  (let [{:keys [union-types]}
        (get-in schema [:unions name])]
    (make-type-map
      {:name           name
       :kind           :UNION
       :possible-types (mapv ->Type union-types)})))

;; ### Scalar Types

(defn- as-scalar-type
  [_ name]
  (make-type-map
    {:name name
     :kind :SCALAR}))

;; ### Input Types

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
  [{:keys [::schema]} name]
  (let [{:keys [fields]}
        (get-in schema [:input-types name])]
    (make-type-map
      {:name         name
       :kind         :INPUT_OBJECT
       :input-fields (as-input-type-fields (vals fields))})))

;; ### Enum Types

(defrecord EnumValues [enum-values include-deprecated]
  data/Resolvable
  (resolve! [_ _]
    (when (seq enum-values)
      (mapv
        (fn [v]
          {:__typename         "__EnumValue"
           :name               v
           :description        nil
           :is-deprecated      false
           :deprecation-reason nil})
        enum-values))))

(defn- as-enum-type
  [{:keys [::schema]} name]
  (let [{:keys [enum-values]} (get-in schema [:enums name])]
    (make-type-map
      {:name        name
       :kind        :ENUM
       :enum-values (->EnumValues enum-values nil)})))

;; ### Dispatch Resolvable

(defn dispatch-type-record
  [{:keys [::schema] :as env} type-name]
  (let [{:keys [type->kind]} schema]
    (case (type->kind type-name)
      :type       (as-object-type env type-name)
      :interface  (as-interface-type env type-name)
      :union      (as-union-type env type-name)
      :scalar     (as-scalar-type env type-name)
      :enum       (as-enum-type env type-name)
      :input-type (as-input-type env type-name))))

(defrecord Type [name]
  data/Resolvable
  (resolve! [_ env]
    (dispatch-type-record env name)))

;; ## Directives

(defrecord Directive [name directive]
  data/Resolvable
  (resolve! [_ _]
    (let [{:keys [directive-locations arguments]} directive]
      {:__typename  "__Directive"
       :name        name
       :description nil
       :locations   (mapv
                      (fn [location]
                        (-> (clojure.core/name location)
                            (string/replace "-" "_")
                            (string/upper-case)))
                      directive-locations)
       :args        (as-arguments (vals arguments))})))

;; ## Middleware

(defn- strip-introspection-fields
  [schema]
  (if-let [root-type (get-in schema [:schema-root :schema-root-types "query"])]
    (update-in schema [:types root-type :fields] dissoc "__schema" "__type")
    schema))

(defn wrap-introspection
  "Wrap the given engine to allow usage of [[->Schema]] and [[->Type]]
   resolvables for GraphQL schema introspection.

   `analyzed-schema` has to conform to `:::schema`."
  [engine analyzed-schema]
  (let [schema (strip-introspection-fields analyzed-schema)]
    (->> (fn [resolver]
           (fn [env batch]
             (resolver
               (assoc env ::schema schema)
               batch)))
         (engine/wrap engine))))
