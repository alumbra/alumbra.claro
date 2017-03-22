(ns alumbra.claro.projection
  (:require [alumbra.claro
             [coercion :as c]
             [values :as v]]
            [claro.data :as data]
            [claro.data.ops :as ops]
            [claro.projection :as projection]))

(declare block->projection)

;; ## Directives

(defn- process-directive
  "Alter the currently processed projection using the given directive."
  [{:keys [directives] :as opts}
   projection
   {:keys [directive-name arguments]}]
  (if-let [directive-fn (get directives directive-name)]
    (->> arguments
         (v/process-arguments opts)
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
    (-> (v/process-arguments opts arguments)
        (projection/parameters projection))
    projection))

;; ## Type Conditions

(defn- process-type-condition
  "Wrap the given projection to only apply to the given (GraphQL) types. This
   requires the `:__typename` field to be given within the result.

   Note that canonicalization should probably already remove fragments
   that reference the current scope type or one of its interfaces/unions."
  [opts {:keys [type-condition]} projection]
  (if (seq type-condition)
    (projection/conditional
      (projection/extract :__typename)
      (set type-condition)
      projection)
    projection))

;; ## Fields

(defn- coerced-leaf
  [opts  {:keys [type-name]}]
  (if-let [coercer (c/output-coercer opts type-name)]
    (projection/prepare #(ops/then % coercer) projection/leaf)
    projection/leaf))

(defn- nullable-value
  [_ projection]
  (projection/maybe projection))

(defn- non-nullable-value
  [{:keys [field-name type-name]} projection]
  (projection/transform
    (fn [value]
      (if (nil? value)
        (data/error
          (format "field '%s' returned 'null' but type '%s!' is non-nullable."
                  field-name
                  type-name))
        value))
    projection))

(defn- field-spec->projection
  "Generate a projection for a `:alumbra.spec.canonical-operation/field-spec`
   value."
  [opts {:keys [field-type non-null? field-spec] :as spec}]
  (cond->>
    (case field-type
      :leaf   (coerced-leaf opts spec)
      :object (block->projection opts spec)
      :list   [(field-spec->projection opts field-spec)])
    (not non-null?) (nullable-value spec)
    non-null?       (non-nullable-value spec)))

(defn- key-for-field
  "Generate the key for the given field. Will use `key-fn` to generate it
   from the raw field name/alias string."
  [{:keys [key-fn]}
   {:keys [field-name field-alias]}]
  (let [field-key (key-fn field-name)]
    (cond (not= field-name field-alias) (projection/alias field-alias field-key)
          (not= field-key field-name)   (projection/alias field-name field-key)
          :else field-key)))

(defn- field->projection
  "Generate a projection for a single field. The result will be a map
   projection with a single key."
  [opts field]
  (some->> (field-spec->projection opts field)
           (process-arguments opts field)
           (hash-map (key-for-field opts field))
           (process-directives opts field)))

;; ## Selection Set

(defn- field?
  "Check whether the given selection map represents a field."
  [{:keys [field-name]}]
  (some? field-name))

(defn- selection-set->projection
  "Generate a projection for a value containing a selection set."
  [opts {:keys [selection-set]}]
  (if-let [templates (seq (keep
                            (fn [selection]
                              (if (field? selection)
                                (field->projection opts selection)
                                (block->projection opts selection)))
                            selection-set))]
    (projection/merge* templates)
    {}))

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
