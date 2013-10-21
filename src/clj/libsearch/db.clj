(ns libsearch.db
  (:use [datomic.api :only [db q] :as d])
  (:require [clj-time.coerce :as coerce]))

(def uri "datomic:free://localhost:4334/lib")

(def schema [

 ; lib

 {:db/id #db/id [:db.part/db]
  :db/ident :lib/name
  :db/unique :db.unique/identity
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db/fulltext true
  :db/doc "lib name"
  :db.install/_attribute :db.part/db}

 {:db/id #db/id [:db.part/db]
  :db/ident :lib/repo
  :db/valueType :db.type/ref
  :db/cardinality :db.cardinality/many
  :db/fulltext true
  :db/doc "repos this lib is used in"
  :db.install/_attribute :db.part/db}

 ; repo

 {:db/id #db/id [:db.part/db]
  :db/ident :repo/fullname
  :db/unique :db.unique/identity
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db/fulltext true
  :db/doc "repo full name, e.g. nicksellen/libsearch"
  :db.install/_attribute :db.part/db}

 {:db/id #db/id [:db.part/db]
  :db/ident :repo/created-at
  :db/valueType :db.type/instant
  :db/cardinality :db.cardinality/one
  :db/fulltext true
  :db/doc "repo creation date"
  :db.install/_attribute :db.part/db}

 {:db/id #db/id [:db.part/db]
  :db/ident :repo/updated-at
  :db/valueType :db.type/instant
  :db/cardinality :db.cardinality/one
  :db/fulltext true
  :db/doc "repo last updated date"
  :db.install/_attribute :db.part/db}

 {:db/id #db/id [:db.part/db]
  :db/ident :repo/watchers-count
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one
  :db/fulltext false
  :db/doc "how many watchers"
  :db.install/_attribute :db.part/db}

 {:db/id #db/id [:db.part/db]
  :db/ident :repo/forks-count
  :db/valueType :db.type/long
  :db/cardinality :db.cardinality/one
  :db/fulltext false
  :db/doc "how many forks"
  :db.install/_attribute :db.part/db}

])

(defn create-db [] (d/create-database uri))

(create-db)

(def conn (d/connect uri))

(defn load-schema []
  (d/transact (d/connect uri) schema))

(defn add-lib [name & repo-names]
  (comment println (str "adding lib [" name "] and repos " repo-names))
  (let [repos (map
               #(into {} [[:repo/fullname %] [:db/id (d/tempid :db.part/user)]])
               repo-names)]
    @(d/transact conn (conj repos {
                               :lib/name name,
                               :lib/repo (map :db/id repos),
                               :db/id (d/tempid :db.part/user)}))))

(defn add-repo [name created updated watchers forks & lib-names]
  (comment println
   (str "adding repo [" name "] with libs " lib-names " and created/updated " created "/" updated ))
  (let [repo-id (d/tempid :db.part/user)
        libs (map
              #(into {} [[:lib/name %]
                         [:lib/repo repo-id]
                         [:db/id (d/tempid :db.part/user)]])
              lib-names)]
    @(d/transact conn (conj
                       libs
                       {:repo/fullname name
                        :repo/created-at created
                        :repo/updated-at updated
                        :repo/watchers-count watchers
                        :repo/forks-count forks
                        :db/id repo-id}))))

(defn list-repos []
  (map first (d/q '[:find ?n :where [_ :repo/fullname ?n]] (db conn))))

(defn list-libs []
  (map first (d/q '[:find ?n :where [_ :lib/name ?n]] (db conn))))

(defn query []
  (map #(into {} (seq (d/entity (db conn) (first %))))
       (q '[:find ?n :where [?n :lib/name]]
          (db conn))))

(defn p [v]
  ;(prn v)
  v)

(defn lang [val]
  [[(list '.startsWith '?libname (str val "/"))]])

(defn watchers [f val]
  [['?repo :repo/watchers-count '?watchers] [(list f '?watchers val)]])

(defn forks [f val]
  [['?repo :repo/forks-count '?forks] [(list f '?forks val)]])

;; these date ones aren't very good yet... I want to be able to
;; pass the comparison fn into it and I don't know how to reference
;; properly inside my inner function

(defn created [f val]
  [['?repo :repo/created-at '?created] [(list f '?created val)]])

(defn created> [val]
  (created #(> (coerce/to-long %1) %2) (coerce/to-long val)))

(defn created< [val]
  (created #(< (coerce/to-long %1) %2) (coerce/to-long val)))

(defn updated [f val]
  [['?repo :repo/updated-at '?updated] [(list f '?updated val)]])

(defn mapize-results [ks results]
  (map #(apply assoc {} (interleave ks (take (count ks) %))) results))

(defn with-count-and-offset [count offset results]
  (->> results
       (drop offset)
       (take count)))

(defn query-with-rules [query rules]
  (sort #(> (first %1) (first %2))
          (d/q (p (update-in query [:where] concat (filter seq (apply concat rules))))
               (db conn))))

(defn query-libs-with-repos [& rules]
  (query-with-rules {:find ['(count ?repowatchers)
                            '?libname
                            '(distinct ?reponame)]
                     :in ['$]
                     :where [['?lib :lib/name '?libname]
                             ['?lib :lib/repo '?repo]
                             ['?repo :repo/fullname '?reponame]
                             ['?repo :repo/watchers-count '?repowatchers]]}
                    rules))

(defn query-repos-with-libs [& rules]
  (query-with-rules {:find ['?repowatchers
                            '?reponame
                            '(distinct ?libname)
                            '?repowatchers
                            '?repoforks
                            '?created
                            '?updated]
                     :in ['$]
                     :where [['?repo :repo/fullname '?reponame]
                             ['?lib :lib/repo '?repo]
                             ['?lib :lib/name '?libname]
                             ['?repo :repo/watchers-count '?repowatchers]
                             ['?repo :repo/forks-count '?repoforks]
                             ['?repo :repo/created-at '?created]
                             ['?repo :repo/updated-at '?updated]]}
                    rules))

(defn query-repos-with-libs-off []
  (take 20 (d/q '[:find ?reponame ?libname
           :where
           [?repo-id :repo/fullname ?reponame]
           [?lib-id :lib/repo ?repo-id]
           [?lib-id :lib/name ?libname]]
         (db conn))))

(defn libs [count offset & rules]
  (->> (apply query-libs-with-repos rules)
       (with-count-and-offset count offset)
       (mapize-results [:rank :lib :repos])))

(defn repos [count offset & rules]
  (->> (apply query-repos-with-libs rules)
       (with-count-and-offset count offset)
       (mapize-results [:rank :repo :libs :watchers :forks :created :updated])))
