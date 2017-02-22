(ns alumbra.claro.introspection-test
  (:require [alumbra.claro.fixtures :as fix]
            [clojure.test :refer :all]))

(def schema
  (fix/schema
    "type Person { name: String!, pets: [Pet!] }
     interface Pet { name: String! }
     type HouseCat implements Pet { name: String!, owner: Person!, meowVolume: Int }
     type HouseDog implements Pet { name: String!, owner: Person!, barkVolume: Int }
     type Cat implements Pet { name: String!, meowVolume: Int }
     type Dog implements Pet { name: String!, barkVolume: Int }
     type QueryRoot { me: Person!, allPeople: [Person!] }
     schema { query: QueryRoot }"))

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
  (let [execute! (fix/execute-fn {:schema schema, :query {}})
        {:keys [data]} (execute! graphiql-introspection-query)
        {:strs [__schema]} data
        {:strs [queryType mutationType subscriptionType types directives]} __schema]
    (is (map? data))
    (is (map? __schema))

    (testing "root types are exposed."
      (is (= {"name" "QueryRoot"} queryType))
      (is (nil? mutationType))
      (is (nil? subscriptionType)))

    (testing "all other types are exposed."
      (is (= #{"Boolean" "Cat" "Dog" "Float" "HouseCat"
               "HouseDog" "ID" "Int" "Person" "Pet"
               "QueryRoot" "String" "__Directive" "__DirectiveLocation"
               "__EnumValue" "__Field" "__InputValue" "__Schema"
               "__Type" "__TypeKind"}
             (set (map #(get % "name") types)))))

    (testing "kinds are correct."
      (let [kind->count (frequencies (map #(get % "kind") types))]
        (is (= {"SCALAR"    5
                "ENUM"      2
                "INTERFACE" 1
                "OBJECT"    12}
               kind->count))))

    (testing "object/interface/input type fields."
      (doseq [{:strs [kind fields], type-name "name"} types
              :when (contains? #{"OBJECT" "INPUT_TYPE" "INTERFACE"} kind)
              :when (is (seq fields)
                        (format "%s is missing fields." type-name))
              {:strs [name]} fields]
        (is (string? name))))

    (testing "enum values."
      (doseq [{:strs [kind enumValues], type-name "name"} types
              :when (= kind "ENUM")
              :when (is (seq enumValues)
                        (format "%s is missing enum values." type-name))
              {:strs [name]} enumValues]
        (is (string? name))))

    (testing "directives are exposed."
      (is (= #{"skip" "include" "deprecated"}
             (set (map #(get % "name") directives)))))

    (testing "fields are sorted."
      (doseq [{:strs [name fields]} types
              :when fields
              :let [field-names (map #(get % "name") fields)]]
        (is (= (sort field-names) field-names)
            (str "fields not sorted for type: " name))))))
