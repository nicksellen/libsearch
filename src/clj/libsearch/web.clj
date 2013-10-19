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
  (map #(apply assoc % (interleave [:lang :name] (clojure.string/split (:lib %) #"\/" 2))) libs))

(defn process-repos [repos]
  repos)

(defroutes app-routes
  (GET "/libs" [] (json (process-libs (db/list-libs-with-repos))))
  (GET "/repos" [] (json (process-repos (db/list-repos-with-libs)))))


(def handler
  (-> app-routes
      site
      (resources/wrap-resource "public")
      (wrap-reload '(libsearch.web))))

(defn run [port] (run-jetty handler {:port port, :join? false}))
