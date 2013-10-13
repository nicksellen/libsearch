(ns libsearch.web
  (:use ring.adapter.jetty)
  (:require [libsearch.db :as db]))

(defn web-handler [request]
  {:status 200
   :headers { "Content-Type" "text/html"}
   :body "Hey!"})

(defn run [port] (run-jetty web-handler {:port port}))
