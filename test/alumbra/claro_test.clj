(ns alumbra.claro-test
  (:require [clojure.test.check
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [alumbra.generators :as alumbra-gen]
            [alumbra.claro.fixtures :as fix]))

(def gen-operation
  (alumbra-gen/operation fix/schema))

(defspec t-execution 500
  (prop/for-all
    [operation-string (gen-operation :query)]
    (map? (fix/execute! operation-string))))
