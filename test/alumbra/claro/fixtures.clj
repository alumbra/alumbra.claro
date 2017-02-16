(ns alumbra.claro.fixtures
  (:require [alumbra.analyzer :as analyzer]
            [alumbra.parser :as parser]
            [alumbra.claro :as claro]
            [claro.data :as data]))

;; ## Schema

(defn schema
  [schema-string]
  (analyzer/analyze-schema
    schema-string
    parser/parse-schema))

(def parse
  #(parser/parse-document %))

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
