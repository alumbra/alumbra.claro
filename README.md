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

### Executor Construction

```clojure
(require '[alumbra.analyzer :as analyzer]
         '[alumbra.parser :as parser]
         '[alumbra.claro :as claro])
```

An executor is based on a GraphQL schema which can be constructed by
using e.g. alumbra's own [analyzer][alumbra-analyzer] and
[parser][alumbra-parser]:

```clojure
(def schema
  (analyzer/analyze-schema
    "type Person { id: ID!, name: String!, friends: [Person!] }
     type QueryRoot { person(id: ID!): Person }
     schema { query: QueryRoot }"
    parser/parse-schema))
```

[alumbra-analyzer]: https://github.com/alumbra/alumbra.analyzer
[alumbra-parser]: https://github.com/alumbra/alumbra.parser

Now, we define our [claro][claro] resolvables, ideally one for each non-root
type declared in our schema:

```clojure
(require '[claro.data :as data])

(defrecord Person [id]
  data/Resolvable
  (resolve! [_ {:keys [db]}]
    ...))
```

The root types have to be given as plain maps of resolvables, e.g. for our
`QueryRoot`:

```clojure
(def QueryRoot
  {:person (->Person nil)})
```

A minimal executor can now be constructed using:

```clojure
(def executor
  (claro/executor
    {:schema schema
     :env    {:db ...}
     :query  QueryRoot}))
```

### Executor Usage

An executor is a function taking a context map, as well as a _canonicalized_
operation, producing a result map with `:data` and optionally `:errors` keys.

```clojure
(->> "{ person(id: \"ID\") { name } }"
     (parser/parse-document)
     (analyzer/canonicalize-operation schema)
     (executor {:session ...}))
;; => {:data ..., :errors ...}
```

The context map (`{:session ...}` in this case) will be merged into the claro
environment supplied to resolvables. Note that you should validate the GraphQL
document between parsing and canonicalization, e.g. using alumbra's
[validator][alumbra-validator].

[alumbra-validator]: https://github.com/alumbra/alumbra.validator

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
