(ns libsearch.core
  (:use clojure.pprint)
  (:require [libsearch.db :as db])
  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as coerce])
  (:require [tentacles.core :as core])
  (:require [clojure.stacktrace :as stacktrace])
  (:require [tentacles.repos :as repos]))

(def opts {:auth (System/getenv "GHAUTH")})

(def counter (atom 0))

(defn counter-inc []
  (swap! counter inc))

(defn counter-reset []
  (swap! counter (fn [_] 0)))

(counter-reset)

(defn extract-name-and-repo
  [h]
  (apply assoc h (interleave [:user :repo] (.split (:full_name h) "/"))))

(defn extract-langs
  [h]
  (assoc h :langs (set (keys (repos/languages (:user h) (:repo h) opts)))))

(defn uses-lang? [lang h]
  (contains? (:langs h) lang))

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
  (cond (uses-lang? :Ruby h)
        (assoc h :libs
               (concat (:libs h)
                       (set (map (partial str "ruby/") (concat
                                                  (extract-libs-from-gemspec h)
                                                  (extract-libs-from-gemfile h))))))
        :else h))

(defn load-project-clj [user repo]
  (String. (or (:content (repos/contents user repo "project.clj" opts)) "")))

(defn parse-project-clj [content]
  (cond (empty? content)
        {}
        :else
        (apply assoc {} (drop 3 (read-string content)))))

(defn extract-clojure-libs [h]
  (cond (uses-lang? :Clojure h)
        (assoc h :libs
               (concat (:libs h)
                       (map #(str "clojure/" (first %))
                            (:dependencies (parse-project-clj (load-project-clj
                                                               (:user h)
                                                               (:repo h)))))))
        :else h))

(defn keep-useful-keys [h]
  (select-keys h [:langs
                  :user
                  :created_at
                  :updated_at
                  :pushed_at
                  :watchers_count
                  :forks_count
                  :repo
                  :libs]))

(defn print-preprocess-result [h]
  (println @counter (:full_name h) "\n"
           " langs : " (seq (:langs h)) "\n"
           " libs  : " (seq (:libs h)) "\n")
  (counter-inc)
  h)

(defn add-empty-libs-set [h]
  (assoc h :libs #{}))

(defn preprocess-repo [h]
  (-> h
      extract-name-and-repo
      add-empty-libs-set
      extract-langs
      extract-ruby-libs
      extract-clojure-libs
      print-preprocess-result))

(defn fetch-github-repo-data [h]
  (merge h (select-keys
            (repos/specific-repo (:user h) (:repo h) opts)
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
  (println "  adding repo to db " h "\n")
  (apply db/add-repo (concat [(str (:user h) "/" (:repo h))
                              (:created_at h)
                              (:pushed_at h)
                              (:watchers_count h)
                              (:forks_count h)]
                             (:libs h)))
  h)


(defn add-some-defaults [h]
  (merge h {:created_at -1,
            :updated_at -1,
            :pushed_at -1,
            :watchers_count 0
            :forks_count 0}))

(defn add-libs-to-db [h]
  (map #(db/add-lib %) (:libs h)))

(defn print-data [h]
  (println "data: " h)
  h)

(defn postprocess-repo [h]
  (println "  selected!\n")
  (-> h
      add-some-defaults
      fetch-github-repo-data
      parse-dates
      keep-useful-keys
      print-data
      add-repo-to-db
      add-libs-to-db))

(defn repo-filter [h]
  (not-empty (:libs h)))

(defn fetch-repos [n since]
  (counter-reset)
  (->> (repos/all-repos (assoc opts :since since :all-pages true))
       (take n)
       (filter seq)
       (pmap preprocess-repo)
       (filter repo-filter)
       (pmap postprocess-repo)))
