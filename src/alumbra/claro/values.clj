(ns alumbra.claro.values
  (:require [alumbra.claro.coercion :as c]))

(declare process-value)

(defn- process-key
  [{:keys [key-fn]} k]
  (key-fn k))

(defn- process-object
  [opts o]
  (->> (for [[k v] o]
         [(process-key opts k)
          (process-value opts v)])
       (into {})))

(defn- process-scalar
  [opts {:keys [type-name value]}]
  (c/coerce-value opts type-name value))

(defn process-value
  [opts v]
  {:pre [(or (map? v) (sequential? v))]}
  (cond (sequential? v) (mapv #(process-value opts %) v)
        (:type-name v)  (process-scalar opts v)
        :else           (process-object opts v)))

(defn process-arguments
  [opts arguments]
  (process-object opts arguments))
