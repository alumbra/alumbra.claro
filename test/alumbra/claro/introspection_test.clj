(ns alumbra.claro.introspection-test
  (:require [alumbra.claro.fixtures :as fix]
            [clojure.test :refer :all]))

(def ^:private graphiql-introspection-query
  "query {
     __schema {
       queryType { name }
       mutationType { name }
       subscriptionType { name }
       types {
         ...FullType
       }
       directives {
         name
         description
         locations
         args {
           ...InputValue
         }
       }
     }
   }
   fragment FullType on __Type {
     kind
     name
     description
     fields(includeDeprecated: true) {
       name
       description
       args {
         ...InputValue
       }
       type {
         ...TypeRef
       }
       isDeprecated
       deprecationReason
     }
     inputFields {
       ...InputValue
     }
     interfaces {
       ...TypeRef
     }
     enumValues(includeDeprecated: true) {
       name
       description
       isDeprecated
       deprecationReason
     }
     possibleTypes {
       ...TypeRef
     }
   }
   fragment InputValue on __InputValue {
     name
     description
     type { ...TypeRef }
     defaultValue
   }
   fragment TypeRef on __Type {
     kind
     name
     ofType {
       kind
       name
       ofType {
         kind
         name
         ofType {
           kind
           name
           ofType {
             kind
             name
             ofType {
               kind
               name
               ofType {
                 kind
                 name
                 ofType {
                   kind
                   name
                 }
               }
             }
           }
         }
       }
     }
   }")

(deftest t-introspection-query
  (let [{:keys [data]} (fix/execute! graphiql-introspection-query)
        {:strs [__schema]} data
        {:strs [queryType mutationType subscriptionType types directives]} __schema]
    (is (map? data))
    (is (map? __schema))
    (is (= {"name" "QueryRoot"} queryType))
    (is (nil? mutationType))
    (is (nil? subscriptionType))
    (is (= #{"Boolean" "Cat" "Dog" "Emotion" "Float" "HouseCat"
             "HouseDog" "ID" "Int" "Person" "Pet"
             "QueryRoot" "String" "__Directive" "__DirectiveLocation"
             "__EnumValue" "__Field" "__InputValue" "__Schema"
             "__Type" "__TypeKind"}
           (set (map #(get % "name") types))))
    (is (= #{"skip" "include" "deprecated"}
           (set (map #(get % "name") directives))))))
