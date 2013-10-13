(ns libsearch.db
  (:use [datomic.api :only [db q] :as d]))

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
  (println (str "adding lib [" name "] and repos " repo-names))
  (let [repos (map
               #(into {} [[:repo/fullname %] [:db/id (d/tempid :db.part/user)]])
               repo-names)]
    @(d/transact conn (conj repos {
                               :lib/name name,
                               :lib/repo (map :db/id repos),
                               :db/id (d/tempid :db.part/user)}))))

(defn add-repo [name created updated watchers forks & lib-names]
  (println
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

(defn query-libs-with-repos []
  (seq
   (d/q '[:find ?libname ?reponame
          :where
          [?i :lib/name ?libname]
          [?i :lib/repo ?r]
          [?r :repo/fullname ?reponame]]
        (db conn))))

(defn query-repos-with-libs []
  (seq
   (d/q '[:find ?reponame ?libname
          :where
          [?repo-id :repo/fullname ?reponame]
          [?lib-id :lib/repo ?repo-id]
          [?lib-id :lib/name ?libname]]
        (db conn))))

(defn query-result-to-data [r k v]
  (map #(assoc {} k (ffirst %) v (map last %))
       (partition-by first (sort-by first (r)))))

(defn list-libs-with-repos []
  (query-result-to-data query-libs-with-repos :lib :repos))

(defn list-repos-with-libs []
  (query-result-to-data query-repos-with-libs :repo :libs))
