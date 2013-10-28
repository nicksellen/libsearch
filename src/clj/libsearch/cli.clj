(ns libsearch.cli
  (:use clojure.pprint)
  (:require [libsearch.web :as web])
  (:require [libsearch.core :as import])
  (:require [clojure.main :as clj-main])
  (:gen-class :main true))

(defn run-server [& args]
  (let [port (Integer/parseInt (first args))]
    (println "starting HTTP server on port" port)
    (web/run port)))

(defn run-console [& args]
  (println "starting REPL")
  (clj-main/repl))

(defn run-import [& args]
  (let [[since count] (map #(Integer/parseInt %) args)]
    (println "importing" count "repos since" since)
    (dorun (import/fetch-repos count since))))

(defn -main
  [& args]
  (condp = (first args)
    "server" (apply run-server (rest args))
    "console" (apply run-console (rest args))
    "import" (apply run-import (rest args))))
