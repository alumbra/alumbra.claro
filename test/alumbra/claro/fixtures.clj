(ns alumbra.claro.fixtures
  (:require [alumbra.analyzer :as analyzer]
            [alumbra.parser :as parser]
            [alumbra.claro :as claro]
            [claro.data :as data]))

;; ## Schema

(def schema
  (-> "type Person { name: String!, pets: [Pet!] }
       interface Pet { name: String! }
       type HouseCat implements Pet { name: String!, owner: Person!, meowVolume: Int }
       type HouseDog implements Pet { name: String!, owner: Person!, barkVolume: Int }
       type Cat implements Pet { name: String!, meowVolume: Int }
       type Dog implements Pet { name: String!, barkVolume: Int }
       type QueryRoot { me: Person!, allPeople: [Person!] }
       schema { query: QueryRoot }"
      (analyzer/analyze-schema parser/parse-schema)))

(def canonicalize
  (analyzer/canonicalizer schema))

(def parse
  #(parser/parse-document %))

;; ## Helpers

(defn- rand-animal-name
  []
  (rand-nth ["Annie" "Barb" "Chris" "Dan" "Elon"]))

(defn- rand-person-name
  []
  (rand-nth ["Me" "You" "Him" "Her" "Frank"]))

;; ## Resolvables

(declare ->Animal)

(defrecord Person [name]
  data/Resolvable
  (resolve! [_ _]
    {:name name,
     :pets (distinct
             (repeatedly (count name)
                         #(->Animal (rand-animal-name))))}))

(defrecord AllPeople []
  data/Resolvable
  (resolve! [_ _]
    (distinct (repeatedly 10 #(->Person (rand-person-name))))))

(defrecord Cat [name]
  data/Resolvable
  (resolve! [_ _]
    {:name name
     :owner (->Person (rand-person-name))
     :meow-volume (rand-int 10)}))

(defrecord Dog [name]
  data/Resolvable
  (resolve! [_ _]
    {:name name
     :owner (->Person (rand-person-name))
     :bark-volume (rand-int 10)}))

(defrecord Animal [name]
  data/Resolvable
  (resolve! [_ _]
    ((rand-nth [->Cat ->Dog]) name)))

;; ## Root

(def QueryRoot
  {:me         (->Person "Me")
   :all-people (->AllPeople)})

;; ## Execute

(def execute!
  (let [f (claro/make-executor
          {:schema schema
           :query  QueryRoot})]
    (fn [query & [context]]
      (->> query
           (parse)
           (canonicalize)
           (f context)))))
