(ns alumbra.claro.values
  (:require [alumbra.claro.coercion :as c]))

(defprotocol ClaroValue
  (process-value [this opts]))

(defn- process-key
  [{:keys [key-fn]} k]
  (key-fn k))

(extend-protocol ClaroValue
  clojure.lang.Sequential
  (process-value [sq opts]
    (mapv #(process-value % opts) sq))

  clojure.lang.IPersistentMap
  (process-value [{:keys [type-name value] :as o} opts]
    (if type-name
      (c/coerce-value opts type-name value)
      (->> (for [[k v] o]
             [(process-key opts k)
              (process-value v opts)])
           (into {}))))

  Object
  (process-value [this _]
    (throw
      (IllegalArgumentException.
        (str "Unexpected value when preparing arguments: "
             (pr-str this)))))

  nil
  (process-value [_ _]
    nil))

(defn process-arguments
  [opts arguments]
  {:pre [(map? arguments)]}
  (process-value arguments opts))
