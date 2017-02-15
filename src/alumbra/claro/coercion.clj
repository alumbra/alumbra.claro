(ns alumbra.claro.coercion
  (:require [claro.data :as data]
            [camel-snake-kebab.core :as csk]))

(defn- call-coercer
  [f type-name value]
  (try
    (f value)
    (catch Throwable t
      (data/error
        (format "could not coerce value to '%s': %s"
                type-name
                (pr-str value))))))

(defn- wrap-coercer-exception
  [f type-name]
  #(call-coercer f type-name %))

(defn- default-coercer
  [type-name value]
  (case type-name
    ("ID" "String")
    (if (keyword? value)
      (name value)
      (str value))

    "Int"
    (if (string? value)
      (Long/parseLong value)
      (long value))

    "Float"
    (if (string? value)
      (Double/parseDouble value)
      (double value))

    "Boolean"
    (if (string? value)
      (not= value "false")
      (boolean value))

    value))

(defn output-coercer
  [{:keys [schema scalars]} type-name]
  (-> (or (get-in scalars [type-name :encode])
          (let [{:keys [type->kind]} schema]
            (if (= (type->kind type-name) :enum)
              csk/->SCREAMING_SNAKE_CASE_STRING
              #(default-coercer type-name %))))
      (wrap-coercer-exception type-name)))

(defn coerce-value
  [{:keys [schema scalars]} type-name value]
  (-> (or (get-in scalars [type-name :decode])
          (let [{:keys [type->kind]} schema]
            (if (= (type->kind type-name) :enum)
              csk/->kebab-case-keyword
              #(default-coercer type-name %))))
      (call-coercer type-name value)))
