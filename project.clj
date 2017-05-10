(defproject alumbra/claro "0.1.12-SNAPSHOT"
  :description "An alumbra GraphQL executor on top of Claro."
  :url "https://github.com/alumbra/alumbra.claro"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14" :scope "provided"]
                 [alumbra/spec "0.1.9" :scope "provided"]
                 [org.flatland/ordered "1.5.4"]
                 [camel-snake-kebab "0.4.0"]
                 [claro "0.2.18"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [alumbra/parser "0.1.7"]
                                  [alumbra/analyzer "0.1.14"]
                                  [alumbra/generators "0.2.2"]]}}
  :pedantic? :abort)
