(ns libsearch.core
  (:use clojure.pprint)
  (:require [libsearch.db :as db])
  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as coerce])
  (:require [tentacles.core :as core])
  (:require [tentacles.repos :as repos]))

(def opts {:auth (System/getenv "GHAUTH")})

(defn extract-name-and-repo
  [h]
  (apply assoc h (interleave [:user :repo] (.split (:full_name h) "/"))))

(defn extract-langs
  [h]
  (assoc h :langs (set (keys (repos/languages (:user h) (:repo h) opts)))))

(defn uses-ruby? [h]
  (contains? (:langs h) :Ruby))

(defn extract-libs-from-gemfile [h]
  (mapcat rest
   (re-seq #"gem\ ['\"]([a-zA-Z0-9_-]+)['\"]"
           (String. (or (:content
                           (repos/contents
                            (:user h)
                            (:repo h)
                            "Gemfile" opts)) "")))))

(defn extract-libs-from-gemspec [h]
  (mapcat rest
   (re-seq #"_dependency\(['\"]([a-zA-Z0-9_-]+)['\"]"
           (String. (or (:content
                           (repos/contents
                            (:user h)
                            (:repo h)
                            (str (:repo h) ".gemspec") opts)) "")))))

(defn extract-ruby-gemspec [h]
  (cond (uses-ruby? h)
        (assoc h :ruby-libs
               (set (concat
                  (extract-libs-from-gemspec h)
                  (extract-libs-from-gemfile h))))
        :else h))

(defn keep-useful-keys [h]
  (select-keys h [
                  :langs
                  :user
                  :created_at
                  :updated_at
                  :repo
                  :ruby-libs]))

(defn process-repo [h]
  (println ".")
  ((comp
    extract-ruby-gemspec
    extract-langs
    extract-name-and-repo) h))

(defn fetch-dates [h]
  (merge h (select-keys
            (repos/specific-repo (:user h) (:repo h))
            [:created_at :updated_at])))

(defn parse-dates [h]
  (apply assoc h
         (mapcat
          #(list % (coerce/to-long (h %)))
          [:created_at :updated_at])))

(defn add-ruby-libs-to-db [h]
  (map #(db/add-lib % "Ruby" (str (:user h) "/" (:repo h))) (:ruby-libs h)))

(defn post-process-repo [h]
  (println (str "processing " (:full_name h)))
  ((comp
    add-ruby-libs-to-db
    keep-useful-keys
    parse-dates
    fetch-dates) h))

(defn repo-filter [h]
  (and (contains? (:langs h) :Ruby)
       (not-empty (:ruby-libs h))))

(defn fetch-repos [n since]
  (map post-process-repo
       (filter
        repo-filter
        (map process-repo
             (take n (repos/all-repos (assoc opts :since since)))))))

(defn list []
  (doseq [item (db/query)]
    (println (:lib/name item))
    (doseq [repo (:lib/repo item)]
      (println "  " repo))
    (println)))

(pprint "nick")
