(ns alumbra.claro.coercion
  (:require [camel-snake-kebab.core :as csk]))

(defn output-coercer
  [{:keys [schema]} type-name]
  (let [{:keys [type->kind]} schema]
    (if (= (type->kind type-name) :enum)
      csk/->SCREAMING_SNAKE_CASE_STRING )))
