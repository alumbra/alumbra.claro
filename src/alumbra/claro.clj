(ns alumbra.claro
  (:require [alumbra.claro
             [introspection :as introspection]
             [keys :as keys]
             [projection :refer [operation->projection]]
             [typename :as typename]]
            [claro.data.ops :refer [then]]
            [claro.data :as data]
            [claro.engine :as engine]
            [claro.projection :as projection]
            [clojure.walk :as w]
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
  [query {:keys [schema-root]}]
  (then query
        merge
        {:__schema   (introspection/->Schema)
         :__type     (introspection/->Type nil)
         :__typename (get-in schema-root [:schema-root-types "query"])}))

;; ## Options

(defn- prepare-opts
  [{:keys [wrap-key-fn schema]
    :or {wrap-key-fn identity}
    :as opts}]
  (-> opts
      (assoc :key-fn (wrap-key-fn keys/generate-key))
      (update :query add-introspection-resolvables schema)))

;; ## Execution

(defn- generate-env
  [{base-env :env :or {base-env {}}} env]
  (merge base-env env))

(defn- finalize
  [result]
  (let [v (volatile! [])
        data (w/prewalk
               (fn [x]
                 (if (data/error? x)
                   (do (vswap! v conj x) nil)
                   x))
               result)]
    {:data   data
     :errors (mapv
               (fn [error]
                 (let [context (data/error-data error)]
                   (cond-> {:message (data/error-message error)}
                     context (assoc :context context))))
               @v)}))

(defn- run-operation!
  [opts engine env {:keys [operation-type] :as operation}]
  (let [projection (operation->projection opts operation)
        root-value (get opts (keyword operation-type))
        env        (generate-env opts env)
        result (-> root-value
                   (projection/apply projection)
                   (engine {:env env})
                   (deref))]
    (finalize result)))

;; ## Executor Creation

(defn make-executor
  "Generate a claro-based executor for an operation conforming to
   `:alumbra/canonical-operation`.

   - `:schema` (__required__): a value conforming to `:alumbra/analyzed-schema`
     used for exposing introspection facilities.
   - `:env`: a base environment map to be based to claro resolvables.

   The following resolvables can be given in `opts`:

   - `:query` (__required__): the root map for query operations,
   - `:mutation`: the root map for mutation operations,
   - `:subscription`: the root map for subscription operations.

   The following options can additionally be given to customize the executor
   behaviour:

   - `:wrap-key-fn`: a wrapper for the key generation function, allowing you to
     customise translation of special keys,
   - `:directive-handlers`: a map associating directive names (without `@`) with
     projection transformation functions.

   Internally, a projection is generated from the query. `:directive-handlers`
   will be used to customize said projection. For example, the `@skip` directive
   could be implemented as follows:

   ```clojure
   (defn skip-handler
     [projection arguments]
     (if (:if arguments)
       projection))
   ```

   The resulting function will take an optional environment map (to be merged
   into the base one) and the canonical operation to resolve."
  ([opts]
   (make-executor (engine/engine) opts))
  ([base-engine opts]
   {:pre [(some? (:query opts)) (some? (:schema opts))]}
   (let [opts   (prepare-opts opts)
         engine (build-executor-engine opts base-engine)]
     (fn claro-executor
       ([operation]
        (claro-executor {} operation))
       ([env operation]
        (run-operation! opts engine env operation))))))
