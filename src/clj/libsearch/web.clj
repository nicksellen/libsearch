(ns libsearch.web
  (:use ring.adapter.jetty)
  (:use ring.middleware.reload)
  (:use compojure.core)
  (:require [clojure.data.json :as json])
  (:require [libsearch.db :as db])
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.handler :refer [site]]
            [ring.middleware.resource :as resources]))

(defn json [data]
  {:status 200
   :headers { "Content-Type" "application/json"}
   :body (json/write-str data)})

(defn process-libs [libs]
  (map #(apply assoc % (interleave [:lang :name] (clojure.string/split (:lib %) #"\/" 2)))
       libs))

(defn process-repos [repos]
  (map #(assoc %
          :created (str (:created %))
          :updated (str (:updated %))
          :libs (map (fn [name] (apply assoc
                                       {}
                                       (interleave [:lang :name]
                                                   (clojure.string/split name #"\/" 2))))
                     (:libs %)))
       repos))

(defn load-libs []
  (process-libs (db/libs 60 0 (db/watchers > 1))))

(defn load-repos []
  (process-repos (db/repos 60 0 (db/watchers > 1))))

(defroutes app-routes
  (GET "/libs" [] (json (load-libs)))
  (GET "/repos" [] (json (load-repos))))

(def handler
  (-> app-routes
      site
      (resources/wrap-resource "public")
      (wrap-reload '(libsearch.web))))

(defn run [port]
  (run-jetty handler {:port port, :join? false}))
