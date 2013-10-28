(ns libsearch.core
  (:use clojure.pprint)
  (:require [libsearch.db :as db])
  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as coerce])
  (:require [tentacles.core :as core])
  (:require [clojure.stacktrace :as stacktrace])
  (:require [tentacles.repos :as repos]))

(def opts (merge {:auth (System/getenv "GHAUTH")} {}))

(def counter (atom 0))

(defn counter-inc []
  (swap! counter inc))

(defn counter-reset []
  (swap! counter (fn [_] 0)))

(counter-reset)

(defn extract-name-and-repo [h]
  (apply assoc h (interleave [:user :repo] (.split (:full_name h) "/"))))

(defn extract-langs [h]
  (assoc h :langs (set (keys (repos/languages (:user h) (:repo h) opts)))))

(defn if-lang? [lang h f]
  (cond (contains? (:langs h) lang) (f h) :else h))

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
   (re-seq #"_dependency[\ \(]['\"]([a-zA-Z0-9_-]+)['\"]"
           (String. (or (:content
                           (repos/contents
                            (:user h)
                            (:repo h)
                            (str (:repo h) ".gemspec") opts)) "")))))

(defn extract-ruby-libs [h]
  (if-lang? :Ruby h
            (fn [h] (update-in h [:libs] #(set (concat %1 %2))
                         (map (partial str "ruby/")
                              (concat (extract-libs-from-gemspec h)
                                      (extract-libs-from-gemfile h)))))))

(defn load-project-clj [user repo]
  (String. (or (:content (repos/contents user repo "project.clj" opts)) "")))

(defn parse-project-clj [content]
  (cond (empty? content) {}
        :else (apply assoc {} (drop 3 (read-string content)))))

(defn extract-clojure-libs [h]
  (if-lang? :Clojure h
            (fn [h] (update-in h [:libs] #(set (concat %1 %2))
                         (map #(str "clojure/" (first %))
                              (:dependencies (parse-project-clj
                                              (load-project-clj (:user h)
                                                                (:repo h)))))))))

(defn keep-useful-keys [h]
  (select-keys h [:id
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
  (println @counter "id:" (:id h) (:full_name h) "\n"
           " langs : " (seq (:langs h)) "\n"
           " libs  : " (seq (:libs h)) "\n")
  (counter-inc)
  h)

(defn add-empty-libs-set [h]
  (assoc h :libs #{}))

(defn log [name h]
  (println name "- h is:" h)
  h)

(defn preprocess-repo [h]
  (try
    (-> h
        extract-name-and-repo
        add-empty-libs-set
        extract-langs
        extract-ruby-libs
        extract-clojure-libs
        print-preprocess-result)
    (catch Throwable t
      (println "exxception caught whilst processing" (:full_name h))
      (.printStackTrace t) h)))

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
  (db/add-repo (str (:user h) "/" (:repo h))
               :created (:created_at h)
               :updated (:pushed_at h)
               :watchers (:watchers_count h)
               :forks (:forks_count h)
               :github-id (:id h)
               :lib-names (:libs h))
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
  (println (:full_name h) "selected!\n")
  (try
    (-> h
        add-some-defaults
        fetch-github-repo-data
        parse-dates
        keep-useful-keys
        add-repo-to-db
        add-libs-to-db)
    (catch Throwable t
      (println "exception caught whilst processing" (str (:user h) "/" (:repo h)))
      (.printStackTrace t) h)))

(defn repo-filter [h]
  (not-empty (:libs h)))

(defn fetch-recursive [& {:keys [fetch proceed-with next-args args]}]
  (let [f (fn f [f-args]
            (let [results (apply fetch f-args)
                  proceed (proceed-with results)]
              (cond proceed (lazy-cat results (f (next-args f-args results)))
                    :else (f f-args))))]
    (f args)))

(defn sleep-until [ts-seconds]
  (let [now-seconds (Math/floor (/ (.getTime (java.util.Date.)) 1000))]
    (cond (> ts-seconds now-seconds)
          (do (println (- ts-seconds now-seconds) "seconds until rate limit resets")
                                         (Thread/sleep 60000)
                                         (recur ts-seconds)))))

(defn rate-limit-remaining []
  (get-in (tentacles.core/api-call :get "rate_limit" nil opts) [:rate :remaining]))

(defn rate-limit-reset []
  (get-in (tentacles.core/api-call :get "rate_limit" nil opts) [:rate :reset]))

(defn handle-ratelimit
  "check if either the last results were rate limited, or we've got less we might
need to complete the next batch"
  [results]
  (cond (= (:status results) 403)
        (do (println "rate limited!")
            (sleep-until (rate-limit-reset))
            false)
        (< (rate-limit-remaining) 500)
        (do (println "rate limit remaining less than 500")
            (sleep-until (rate-limit-reset))
            false)
        :else true))

(defn yaygh [& args]
  (fetch-recursive :fetch (fn gh-fetch [since]
                            (println "fetching repos since:" since)
                            (repos/all-repos (assoc opts :since since)))
                   :args args
                   :next-args #(list (:id (last (butlast %2))))
                   :proceed-with handle-ratelimit))

(defn fetch-repos [n since]
  (counter-reset)
  (->> (yaygh since)
   ;(repos/all-repos (assoc opts :since since :all-pages true))
       (take n)
       (filter seq)
       (pmap preprocess-repo)
       (filter repo-filter)
       (pmap postprocess-repo)))


(defn fib
  ([] (fib 0 1))
  ([a b] (lazy-seq (cons b (fib b (+ a b))))))

(defn fetch-results [offset]
  (if (= (rand-int 3) 1) {:rate-limited true}
      (map #(assoc {} :id (+ offset %)) (range 20))))

(defn oij [blah & {:keys [name] :as m}]
  (list blah m))

(defn yay2 [& args]
  (fetch-recursive :fetch fetch-results
                   :initial-args args
                   :next-args #(list (+ (first %1) (count %2)))
                   :proceed-with (fn [results]
                                   (cond (:rate-limited results)
                                         (do (println "sleeping...")
                                             (Thread/sleep 500))
                                     :else true))))
