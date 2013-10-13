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

(defn extract-ruby-libs [h]
  (cond (uses-ruby? h)
        (assoc h :libs
               (set (map (partial str "ruby/") (concat
                       (extract-libs-from-gemspec h)
                       (extract-libs-from-gemfile h)))))
        :else h))

(defn keep-useful-keys [h]
  (select-keys h [
                  :langs
                  :user
                  :created_at
                  :updated_at
                  :pushed_at
                  :watchers_count
                  :forks_count
                  :repo
                  :libs]))

(defn process-repo [h]
  (println ".")
  ((comp
    extract-ruby-libs
    extract-langs
    extract-name-and-repo) h))

(defn fetch-github-repo-data [h]
  (merge h (select-keys
            (repos/specific-repo (:user h) (:repo h))
            [:created_at
             :updated_at
             :pushed_at
             :watchers_count
             :forks_count])))

(defn parse-dates [h]
  (apply assoc h
         (mapcat
          #(list % (coerce/to-date (h %)))
          [:created_at :updated_at :pushed_at])))

(defn add-repo-to-db [h]
  (apply db/add-repo (concat [(str (:user h) "/" (:repo h))
                              (:created_at h)
                              (:pushed_at h)
                              (:watchers_count h)
                              (:forks_count h)]
                             (:libs h)))
  h)

(defn add-libs-to-db [h]
  (map #(db/add-lib %) (:libs h)))

(defn post-process-repo [h]
  (println (str "processing " (:full_name h)))
  ((comp
    add-libs-to-db
    add-repo-to-db
    keep-useful-keys
    parse-dates
    fetch-github-repo-data) h))

(defn repo-filter [h]
  (not-empty (:libs h)))

(defn fetch-repos [n since]
  (map post-process-repo
       (filter
        repo-filter
        (map process-repo
             (take n (repos/all-repos (assoc opts :since since)))))))

(defn list2 []
  (doseq [item (db/list-repos-with-libs)]
    (println (:repo item) " " (:meta item))
    (doseq [lib (:libs item)]
      (println "  " lib))
    (println)))

(defn list []
  (doseq [item (db/list-libs-with-repos)]
    (println (:lib item))
    (doseq [repo (:repos item)]
      (println "  " repo))
    (println)))

(pprint "nick")
