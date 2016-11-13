(ns alumbra.claro.projection
  (:require [claro.projection :as projection]))

(declare block->projection)

;; ## Helpers

(defn- prepare-map-keys
  "Apply `key-fn` to all keys within the given map."
  [{:keys [key-fn] :or {key-fn keyword}} m]
  {:pre [(map? m)]}
  (->> (for [[k v] m]
         [(key-fn k) v])
       (into {})))

;; ## Directives

(defn- process-directive
  "Alter the currently processed projection using the given directive."
  [{:keys [directive-fns] :as opts}
   projection
   [directive-name {:keys [arguments]}]]
  (if-let [directive-fn (get directive-fns directive-name)]
    (->> (prepare-map-keys opts arguments)
         (directive-fn projection))
    (throw
      (IllegalArgumentException.
        (str "no handler for directive '@" directive-name "' found!")))))

(defn- process-directives
  "Alter the currently processed projection using the directives within
   the canonical source map."
  [opts {:keys [directives]} projection]
  (reduce
    (fn [projection directive]
      (let [projection' (process-directive opts projection directive)]
        (if (nil? projection')
          (reduced nil)
          projection')))
    projection
    directives))

;; ## Arguments

(defn- process-arguments
  "Attach the given arguments to the currently processed projection."
  [opts {:keys [arguments]} projection]
  (if (seq arguments)
    (-> (prepare-map-keys opts arguments)
        (projection/parameters projection))
    projection))

;; ## Type Conditions

(defn- process-type-condition
  "Use the given `conditional-fns` to wrap the current projection to only
   apply to the given (GraphQL) type.

   Note that canonicalization should probably already remove fragments
   that reference the current scope type or one of its interfaces/unions."
  [{:keys [conditional-fns]} {:keys [type-condition]} projection]
  (if type-condition
    (if-let [conditional-fn (get conditional-fns type-condition)]
      (conditional-fn projection)
      (throw
        (IllegalArgumentException.
          (str "no conditional projection function given for type '"
               type-condition "'!"))))
    projection))

;; ## Fields

(defn- field-spec->projection
  "Generate a projection for a `:alumbra.spec.canonical-operation/field-spec`
   value."
  [opts {:keys [field-type non-null? field-spec] :as spec}]
  (cond->
    (case field-type
      :leaf   projection/leaf
      :object (block->projection opts spec)
      :list   [(field-spec->projection opts field-spec)])
    (not non-null?) projection/maybe))

(defn- key-for-field
  "Generate the key for the given field. Will use `key-fn` to generate it
   from the raw field name/alias string."
  [{:keys [key-fn] :or {key-fn keyword}}
   {:keys [field-name field-alias]}]
  (if (not= field-name field-alias)
    (projection/alias
      (key-fn field-alias)
      (key-fn field-name))
    (key-fn field-name)))

(defn- field->projection
  "Generate a projection for a single field. The result will be a map
   projection with a single key."
  [opts field]
  (some->> (field-spec->projection opts field)
           (process-arguments opts field)
           (process-directives opts field)
           (hash-map (key-for-field opts field))))

;; ## Selection Set

(defn- field?
  "Check whether the given selection map represents a field."
  [{:keys [field-name]}]
  (some? field-name))

(defn- selection-set->projection
  "Generate a projection for a value containing a selection set."
  [opts {:keys [selection-set]}]
  (->> (for [selection selection-set]
         (if (field? selection)
           (field->projection opts selection)
           (block->projection opts selection)))
       (filter some?)
       (projection/union*)))

;; ## Conditional Blocks

(defn- block->projection
  "Generate a projection for a selection block (either the top-level operation
   or a conditional block produced by e.g. a fragment)."
  [opts block]
  (->> (selection-set->projection opts block)
       (process-type-condition opts block)
       (process-directives opts block)))

;; ## Operation

(defn operation->projection
  "Generate a projection for the given canonical operation."
  [opts operation]
  (block->projection opts operation))
