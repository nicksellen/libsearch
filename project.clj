(defproject libsearch "0.1.1-SNAPSHOT"
  :description "data about and clojure libraries from github"
  :url "http://github.com/nicksellen/libsearch"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-cljsbuild "0.3.4"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1934"]
                 [clj-time "0.6.0"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [org.clojure/data.json "0.2.3"]
                 [com.datomic/datomic-free "0.8.4218"]
                 [tentacles "0.2.5"]]
  :main libsearch.cli
  ;:hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"]
  :cljsbuild {
    :builds {
      :main {
        :source-paths ["src/cljs"]
        :compiler {:output-to "resources/public/js/cljs.js"
                   :optimizations :advanced
                   :pretty-print true
                   :externs ["externs/js/angular.js"]}
        :jar true}}})
