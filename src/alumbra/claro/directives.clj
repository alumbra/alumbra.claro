(ns alumbra.claro.directives)

(defn skip
  "@skip projection"
  [projection arguments]
  (when-not (:if arguments)
    projection))

(defn include
  "@include projection"
  [projection arguments]
  (when (:if arguments)
    projection))

(defn merge-defaults
  "Merge the default directive handlers into the given handler map."
  [handlers]
  (merge handlers
         {"include" include
          "skip"    skip}))
