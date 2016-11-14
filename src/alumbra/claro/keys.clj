(ns alumbra.claro.keys
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]))

(defn generate-key
  "Generate a map key for the given name. This

   - keeps keys starting with two underscores,
   - converts everything else to kebab case."
  [^String n]
  (if (.startsWith n "__")
    (keyword n)
    (->kebab-case-keyword n)))
