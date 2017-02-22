(defproject alumbra/claro "0.1.3"
  :description "An alumbra GraphQL executor on top of Claro."
  :url "https://github.com/alumbra/alumbra.claro"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14" :scope "provided"]
                 [alumbra/spec "0.1.6" :scope "provided"]
                 [camel-snake-kebab "0.4.0"]
                 [claro "0.2.7"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [alumbra/parser "0.1.4"]
                                  [alumbra/analyzer "0.1.6"]
                                  [alumbra/generators "0.2.2"]]}}
  :pedantic? :abort)
