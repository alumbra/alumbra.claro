# alumbra.claro

This library offers an [alumbra][alumbra] GraphQL executor based on
[claro][claro].

[![Build Status](https://travis-ci.org/alumbra/alumbra.claro.svg?branch=master)](https://travis-ci.org/alumbra/alumbra.claro)
[![Clojars Project](https://img.shields.io/clojars/v/alumbra/claro.svg)](https://clojars.org/alumbra/claro)

This executor can be supplied to the [alumbra.ring][alumbra-ring] handler
function to expose claro resolvables using a [GraphQL][graphql] HTTP endpoint.

[alumbra]: https://github.com/alumbra/alumbra
[claro]: https://github.com/xsc/claro
[alumbra-ring]: https://github.com/alumbra/alumbra.ring
[graphql]: http://graphql.org

## Quickstart

First, we need an [analyzed][alumbra-analyzer] GraphQL schema describing our
resolvables:

```clojure
(require '[alumbra.analyzer :as analyzer])

(def schema
  (analyzer/analyze-schema
    "type Person { id: ID!, name: String!, friends: [Person!] }
     type QueryRoot { person(id: ID!): Person }
     schema { query: QueryRoot }"))
```

[alumbra-analyzer]: https://github.com/alumbra/alumbra.validator

Then, we create our resolvables, e.g.:

```clojure
(require '[claro.data :as data])

(defrecord Person [id]
  data/Resolvable
  (resolve! [_ _]
    {:id      id
     :name    (str "Person #" id)
     :friends (map ->Person (range (inc id) (+ id 10) 2))}))
```

And a root value matching our above `QueryRoot`:

```clojure
(def QueryRoot
  {:person (->Person nil)})
```

Both the schema and the root value can now be supplied to the executor:

```
(require '[alumbra.claro :as claro])

(def executor
  (claro/make-executor
    {:schema schema
     :query  QueryRoot}))
```

The result is a function taking a context value (to be injected into the claro
 environment) and a canonical operation, producing a map of `:data` and
`:errors` based on the resolution result.

## License

```
MIT License

Copyright (c) 2016 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
