(ns alumbra.claro.fixtures
  (:require [alumbra.analyzer :as analyzer]
            [alumbra.parser :as parser]
            [alumbra.claro :as claro]
            [claro.data :as data]))

;; ## Schema

(defn- assert-parseable
  [value]
  (when-let [errors (:alumbra/parser-errors value)]
    (throw
      (ex-info "parsing failed." {:errors errors})))
  value)

(defn schema
  [schema-string]
  (->> (comp assert-parseable parser/parse-schema)
       (analyzer/analyze-schema schema-string)))

(defn parse
  [value]
  (assert-parseable
    (parser/parse-document value)))

;; ## Execute

(defn execute-fn
  [{:keys [schema] :as opts}]
  (let [f (claro/executor opts)
        canonicalize (analyzer/canonicalizer schema)]
    (fn [query & [variables]]
      (as-> query <>
        (parse <>)
        (canonicalize <> nil variables)
        (f {} <>)))))
