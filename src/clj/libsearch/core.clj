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

(defn extract-clojure-libs [h]
  (cond (uses-lang? :Clojure h)
        (assoc h :libs
               (concat (:libs h)
                       (map #(str "clojure/" (first %))
                            (:dependencies (apply assoc
                                                  {}
                                                  (drop 3
                                                        (read-string
                                                         (String. (or (:content (repos/contents
                                                                               (:user h)
                                                                               (:repo h)
                                                                               "project.clj"
                                                                               opts))
                                                                      "")))))))))
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

(defn print-preprocess-result [h]
  (println (:full_name h) "\n"
           " langs : " (seq (:langs h)) "\n"
           " libs  : " (seq (:libs h)) "\n")
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
  (println "  adding repo to db " h "\n")
  (apply db/add-repo (concat [(str (:user h) "/" (:repo h))
                              (get h :created_at -1)
                              (get h :pushed_at -1)
                              (get h :watchers_count -1)
                              (get h :forks_count -1)]
                             (:libs h)))
  h)

(defn add-libs-to-db [h]
  (map #(db/add-lib %) (:libs h)))

(defn post-process-repo [h]
  (println "  selected!\n")
  (-> h
      fetch-github-repo-data
      parse-dates
      keep-useful-keys
      add-repo-to-db
      add-libs-to-db))

(defn repo-filter [h]
  (not-empty (:libs h)))

(defn fetch-repos [n since]
  (map post-process-repo
       (filter
        repo-filter
        (map preprocess-repo
             (take n (repos/all-repos (assoc opts :since since :all-pages true)))))))

(defn list2 []
  (doseq [item (db/list-repos-with-libs)]
    (println (:repo item) " " (:meta item))
    (doseq [lib (:libs item)]
      (println "  " lib))
    (println)))

(defn list1 []
  (doseq [item (db/list-libs-with-repos)]
    (println (:lib item))
    (doseq [repo (:repos item)]
      (println "  " repo))
    (println)))

(defn printest []
  (println "starting")
  (map
     #(do
        (println %)
        (Thread/sleep 1000)
        (str "returned: " %))
     ["hello" "nick" "li" "la"]))

(pprint "nick")

(printest)
