(ns alumbra.claro
  (:require [alumbra.claro
             [directives :as directives]
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
  [{:keys [schema engine]
    :or {engine (engine/engine)}}]
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

(defn- assert-schema-valid
  [schema]
  (assert (not (contains? schema :alumbra/parser-errors))
          (str "schema not valid (parser error): "
               (pr-str schema))))

(defn- prepare-opts
  [{:keys [wrap-key-fn schema]
    :or {wrap-key-fn identity}
    :as opts}]
  (assert-schema-valid schema)
  (-> opts
      (assoc :key-fn (wrap-key-fn keys/generate-key))
      (update :query add-introspection-resolvables schema)
      (update :directives directives/merge-defaults)))

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
               result)
        errors (mapv
                 (fn [error]
                   (let [context (data/error-data error)]
                     (cond-> {:message (data/error-message error)}
                       context (assoc :context context))))
                 @v)]
    (cond-> {:data data}
      (seq errors) (assoc :errors errors))))

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

(defn executor
  "Generate a claro-based executor for an operation conforming to
   `:alumbra/canonical-operation`.

   - `:schema` (__required__): a value conforming to `:alumbra/analyzed-schema`
     used for exposing introspection facilities.
   - `:env`: a base environment map to be based to claro resolvables.
   - `:engine`: a claro engine to be used for resolution.

   The following resolvables can be given in `opts`:

   - `:query` (__required__): the root map for query operations,
   - `:mutation`: the root map for mutation operations,
   - `:subscription`: the root map for subscription operations.

   The following options can additionally be given to customize the executor
   behaviour:

   - `:wrap-key-fn`: a wrapper for the key generation function, allowing you to
     customise translation of special keys,
   - `:directives`: a map associating directive names (without `@`) with
     projection transformation functions.
   - `:scalars`: a map associating scalar type names with a map of `:encode`
     and `:decode` functions.

   Internally, a projection is generated from the query. `:directives`
   will be used to customize said projection. For example, the `@skip` directive
   could be implemented as follows:

   ```clojure
   (defn skip-handler
     [projection arguments]
     (if-not (:if arguments)
       projection))
   ```

   The resulting function will take an optional environment map (to be merged
   into the base one) and the canonical operation to resolve."
  [opts]
  {:pre [(map? (:query opts))
         (or (nil? (:mutation opts)) (map? (:mutation opts)))
         (or (nil? (:subscription opts)) (map? (:subscription opts)))
         (some? (:schema opts))]}
  (let [opts   (prepare-opts opts)
        engine (build-executor-engine opts)]
    (fn claro-executor
      ([operation]
       (claro-executor {} operation))
      ([env operation]
       (run-operation! opts engine env operation)))))
