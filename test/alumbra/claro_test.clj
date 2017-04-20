(ns alumbra.claro-test
  (:require [clojure.test.check
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [claro.data :as data]
            [alumbra.generators :as alumbra-gen]
            [alumbra.claro.fixtures :as fix]))

;; ## Test Schema

(def schema
  (fix/schema
    "type Person { name: String!, pets: [Pet!] }
     interface Pet { name: String! }
     type HouseCat implements Pet { name: String!, owner: Person!, meowVolume: Int }
     type HouseDog implements Pet { name: String!, owner: Person!, barkVolume: Int }
     type Cat implements Pet { name: String!, meowVolume: Int }
     type Dog implements Pet { name: String!, barkVolume: Int }
     type QueryRoot { me: Person!, allPeople: [Person!], error: Boolean }
     schema { query: QueryRoot }"))

;; ## Test Resolvables

(defn- rand-animal-name
  []
  (rand-nth ["Annie" "Barb" "Chris" "Dan" "Elon"]))

(defn- rand-person-name
  []
  (rand-nth ["Me" "You" "Him" "Her" "Frank"]))

;; ## Resolvables

(declare ->Animal)

(defrecord Person [name]
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    {:name name,
     :pets (distinct
             (repeatedly (count name)
                         #(->Animal (rand-animal-name))))}))

(defrecord AllPeople []
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    (distinct (repeatedly 10 #(->Person (rand-person-name))))))

(defrecord Cat [name]
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    {:name name
     :owner (->Person (rand-person-name))
     :meow-volume (rand-int 10)}))

(defrecord Dog [name]
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    {:name name
     :owner (->Person (rand-person-name))
     :bark-volume (rand-int 10)}))

(defrecord Animal [name]
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    ((rand-nth [->Cat ->Dog]) name)))

(defrecord SomeError []
  data/PureResolvable
  data/Resolvable
  (resolve! [_ _]
    (data/error "some error.")))

(def QueryRoot
  {:me         (->Person "Me")
   :error      (->SomeError)
   :all-people (->AllPeople)})

;; ## Tests

(def gen-operation
  (alumbra-gen/operation schema))

(defspec t-execution 500
  (let [execute! (fix/execute-fn
                   {:schema schema
                    :query  QueryRoot})]
    (prop/for-all
      [operation-string (gen-operation :query)]
      (map? (execute! operation-string)))))
