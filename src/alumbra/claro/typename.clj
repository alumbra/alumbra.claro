(ns alumbra.claro.typename
  (:require [claro.engine :as engine]
            [claro.data.ops :as ops]
            [claro.data.tree :as tree]
            [claro.runtime.impl :as impl]))

(defn- set-typename-if-map
  "Set the `__typename` key on the given value."
  [typename-fn resolvable result]
  (if (map? result)
    (update result :__typename #(or % (typename-fn resolvable result)))
    result))

(defn- set-typename
  "Eagerly set the typename on the given resolution result."
  [typename-fn resolvable result]
  (->> #(set-typename-if-map typename-fn resolvable %)
       (ops/then result)
       (tree/wrap-tree)))

(defn- attach-typename
  "Attach the `__typename` field to all resolution results based on the
   original resolvable."
  [typename-fn results]
  (->> (for [[resolvable result] results]
         [resolvable (set-typename typename-fn resolvable result)])
       (into {})))

(defn wrap-typename
  "Wrap the given engine to attach the field `__typename` to each resolution
   result. `(typename-fn resolvable result)` is used to generate it."
  [engine typename-fn]
  (let [impl      (engine/impl engine)
        attach-fn #(attach-typename typename-fn %)]
    (->> (fn [resolver]
           (fn [env batch]
             (impl/chain1
               impl
               (resolver env batch)
               attach-fn)))
         (engine/wrap engine))))
