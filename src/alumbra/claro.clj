(ns alumbra.claro
  (:require [alumbra.claro
             [introspection :as introspection]
             [keys :as keys]
             [projection :refer [operation->projection]]
             [typename :as typename]]
            [claro.data.ops :refer [then]]
            [claro.engine :as engine]
            [claro.projection :as projection]
            [potemkin :refer [defprotocol+]]))

;; ## Protocol

(defprotocol+ GraphQL
  "Protocol for attaching GraphQL information to resolvables."
  (__typename [resolvable result]
    "Return a value for the `__typename` field based on a resolvable
     and its result. By default, the simple name of the resolvable
     class is used."))

(extend-protocol GraphQL
  Object
  (__typename [v _]
    (.getSimpleName (class v))))

;; ## Engine

(defn- build-executor-engine
  [{:keys [schema]} engine]
  (-> engine
      (introspection/wrap-introspection schema)
      (typename/wrap-typename #(__typename %1 %2))))

;; ## Introspection

(defn- add-introspection-resolvables
  [query]
  (then query
        merge
        {:__schema (introspection/->Schema)
         :__type   (introspection/->Type nil)}))

;; ## Options

(defn- prepare-opts
  [{:keys [wrap-key-fn]
    :or {wrap-key-fn identity}
    :as opts}]
  (-> opts
      (assoc :key-fn (wrap-key-fn keys/generate-key))
      (update :query add-introspection-resolvables)))

;; ## Execution

(defn- generate-env
  [{:keys [context-key
           env]
    :or {context-key :context}}
   context]
  (merge
    env
    {context-key context}))

(defn- run-operation!
  [opts engine context {:keys [operation-type] :as operation}]
  (let [projection (operation->projection opts operation)
        root-value (get opts (keyword operation-type))
        env        (generate-env opts context)
        result (-> root-value
                   (projection/apply projection)
                   (engine {:env env})
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
   - `:wrap-key-fn`: a wrapper for the key generation function, allowing you to
     customise translation of special keys,
   - `:directive-fns`: a series of functions describing handling of directives,
   - `:conditional-fns`: a series of functions describing handling of
   conditional blocks.

   Internally, a projection is generated from the query. Both `:conditional-fns`
   and `:directive-fns` are used to customize said projection."
  ([opts]
   (make-executor (engine/engine) opts))
  ([base-engine opts]
   {:pre [(some? (:query opts)) (some? (:schema opts))]}
   (let [opts   (prepare-opts opts)
         engine (build-executor-engine opts base-engine)]
     (fn claro-executor
       [context operation]
       (run-operation! opts engine context operation)))))
