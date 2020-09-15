(ns verter.store.sqlite
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :as qb]
            [next.jdbc.date-time]
            [next.jdbc.result-set :as jdbcr]
            [clojure.edn :as edn]
            [taoensso.nippy :as nippy]
            [verter.core :as v]
            [verter.tools :as vt]
            [inquery.core :as q]))

(defn- make-insert-batch-query [facts tx-time]
  (let [rows (mapv (partial v/to-row tx-time)
                   facts)
        [qhead & data] (qb/for-insert-multi :facts
                                            [:key :value :hash :at]
                                            rows
                                            {})
        qfoot " on conflict do nothing"]
    (concat [(str qhead qfoot)] data)))

(defn- inserted-hashes [facts]
  (reduce (fn [a {:keys [hash key]}]
            (-> a
                (update :hashes conj hash)
                (update :kvs conj {:key key
                                   :hash hash})))
    {:hashes [] :kvs []}
    facts))

(defn- last-inserted [rs]
  ;; [{:last_insert_rowid() 42}]
  (-> rs first vals first))

(defn- record-transaction [{:keys [ds queries]}
                           tx-time inserted]
  (let [{:keys [kvs hashes]} (->> inserted
                                  (map (partial v/to-khash tx-time))
                                  inserted-hashes)
        sql (-> (queries :record-transaction))]
    (->> (jdbc/execute! ds [sql tx-time (nippy/freeze hashes)]
                        {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
         last-inserted
         (assoc {:at (vt/inst->date tx-time)
                 :facts kvs} :tx-id))))

(defn- record-facts [{:keys [ds]} facts tx-time]
  (let [sql (make-insert-batch-query facts tx-time)
        recorded (jdbc/execute! ds sql)]
    (if (= (-> recorded first :next.jdbc/update-count) 0)
      ;;TODO: think how to deal with partial inserts on a batch insert with SQLite since it only returns last inserted row id
      (println "no changes to identities were detected, and hence, these facts" facts "were NOT added at" (str tx-time))
      facts)))

(defn- find-facts
  "find all the facts about identity upto a certain time"
  [{:keys [ds queries]} id ts]
  (let [sql (-> queries
                :find-facts-up-to
                (q/with-params {:key (str id)}))]
    (with-open [conn (jdbc/get-connection ds)]
      (->> (jdbc/execute! conn [sql ts]
                          {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
           (mapv (comp
                   #(update % :at vt/ts->date)
                   v/from-row))))))

(defrecord Sqlite [ds queries]
  v/Identity

  (facts [this id]                                        ;; find facts up until now
     (find-facts this id (vt/now)))

  (facts [this id ts]                                     ;; find facts up until a given time
     (find-facts this id ts))

  (add-facts [{:keys [ds] :as db} facts]   ;; add one or more facts
    (when (seq facts)
      (let [tx-time (vt/now)]
        (jdbc/with-transaction [tx ds]
          (some->> (record-facts db facts tx-time)
                   (record-transaction db tx-time))))))

  (obliterate [this id]))                                 ;; "big brother" move: idenitity never existed

(defn connect [ds opts]
   (->Sqlite ds
             (v/load-queries "sqlite")))

(defn create-institute-of-time
  ([ds]
   (create-institute-of-time ds {}))
  ([ds opts]
   (let [qs (-> (v/load-queries "sqlite")
                :create-institute-of-time
                vt/to-multi-queries)]
     (with-open [conn (jdbc/get-connection ds)]
       (mapv (fn [sql]
               (jdbc/execute! conn [sql]))
             qs)))))
