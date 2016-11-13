(ns alumbra.claro
  (:require [alumbra.claro
             [introspection :as introspection]
             [projection :refer [operation->projection]]]
            [claro.data.ops :refer [then]]
            [claro.engine :as engine]
            [claro.projection :as projection]))

;; ## Engine

(defn- build-executor-engine
  [{:keys [schema]} engine]
  (-> engine
      (introspection/wrap-introspection schema)))

;; ## Introspection

(defn- add-introspection-resolvables
  [{:keys [key-fn] :or {key-fn keyword}} query]
  (then query
        merge
        {(key-fn "__schema") (introspection/->Schema)
         (key-fn "__type")   (introspection/->Type nil)}))

;; ## Options

(defn- prepare-opts
  [opts]
  (-> opts
      (update :query #(add-introspection-resolvables opts %))))

;; ## Execution

(defn- run-operation!
  [{:keys [context-key]
    :or {context-key :context}
    :as opts}
   engine
   context
   {:keys [operation-type] :as operation}]
  (let [projection (operation->projection opts operation)
        root-value (get opts (keyword operation-type))
        result (-> root-value
                   (projection/apply projection)
                   (engine {:env {context-key context}})
                   (deref))]
    ;; TODO: collect errors and push to top-level
    {:data result}))

;; ## Executor Creation

(defn make-executor
  "Generate a claro-based executor for an operation conforming to
   `:alumbra/canonical-operation`. Introspection can be done based on
   the required `:schema` key which needs to conform to
   `:alumbra/analyzed-schema`.

   The following resolvables can be given in `opts`:

   - `:query` (__required__): the root resolvable for query operations,
   - `:mutation`: the root resolvable for mutation operations,
   - `:subscription`: the root resolvable for subscription operations.

   The following options can additionally be given to customize the executor
   behaviour:

   - `:context-key`: the key within the claro `env` map to store the context at,
   - `:key-fn`: a function applied to each field/directive/argument name,
   - `:directive-fns`: a series of functions describing handling of directives,
   - `:conditional-fns`: a series of functions describing handling of
   conditional blocks.

   Internally, a projection is generated from the query. Both `:conditional-fns`
   and `:directive-fns` are used to customize said projection."
  ([opts]
   (make-executor (engine/engine) opts))
  ([base-engine {:keys [query mutation subscription schema
                        context-key key-fn directive-fns conditional-fns]
                 :as opts}]
   {:pre [(some? query) (some? schema)]}
   (let [opts   (prepare-opts opts)
         engine (build-executor-engine opts base-engine)]
     (fn claro-executor
       [context operation]
       (run-operation! opts engine context operation)))))
