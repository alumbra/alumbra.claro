(ns alumbra.claro.coercion
  (:require [claro.data :as data]
            [camel-snake-kebab.core :as csk]))

(defn- default-coercer
  [type-name value]
  (try
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

      value)
    (catch Throwable t
      (data/error
        (format "could not coerce value to '%s': %s"
                type-name
                (pr-str value))))))

(defn output-coercer
  [{:keys [schema]} type-name]
  (let [{:keys [type->kind]} schema]
    (if (= (type->kind type-name) :enum)
      csk/->SCREAMING_SNAKE_CASE_STRING
      #(default-coercer type-name %))))

(defn coerce-value
  [{:keys [schema]} type-name value]
  (let [{:keys [type->kind]} schema]
    (if (= (type->kind type-name) :enum)
      (csk/->kebab-case-keyword value)
      (default-coercer type-name value))))
