(ns verter.store.postgres
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
        qfoot " on conflict on constraint fact_uq
                            do nothing"]
    (concat [(str qhead qfoot)] data)))

(defn- inserted-hashes [facts]
  (reduce (fn [a {:keys [hash key]}]
            (-> a
                (update :hashes conj hash)
                (update :kvs conj {:key (edn/read-string key)
                                   :hash hash})))
    {:hashes [] :kvs []}
    facts))

(defn- record-transaction [{:keys [ds schema queries]}
                           tx-time inserted]
  (let [{:keys [kvs hashes]} (inserted-hashes inserted)
        sql (-> (queries :record-transaction)
                (q/with-params {:schema {:as schema}}))]
    (->> (jdbc/execute! ds [sql tx-time (nippy/freeze hashes)]
                            {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
         (mapv (fn [{:keys [id at]}]
                 {:tx-id id
                  :at at
                  :facts kvs})))))

(defn- record-facts [ds facts tx-time]
  (let [sql (make-insert-batch-query facts tx-time)
        recorded (jdbc/execute! ds sql
                                {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})]
    (or (seq recorded)
        (println "no changes to identities were detected, and hence, these facts" facts "were NOT added at" (str tx-time)))))

(defn- find-facts
  "find all the facts about identity upto a certain time"
  [{:keys [ds schema queries]} id ts]
  (let [sql (-> queries
                :find-facts-up-to
                (q/with-params {:key (str id)
                                :schema {:as schema}}))]
    (with-open [conn (jdbc/get-connection ds)]
      (->> (jdbc/execute! conn [sql ts]
                          {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
           (mapv v/from-row)))))

(defrecord Postgres [ds schema queries]
  v/Identity

  (facts [this id]                                        ;; find facts up until now
     (find-facts this id (vt/now)))

  (facts [this id ts]                                     ;; find facts up until a given time
     (find-facts this id ts))

  (add-facts [{:keys [ds schema queries] :as db} facts]   ;; add one or more facts
    (when (seq facts)
      (let [tx-time (vt/now)]
        (jdbc/with-transaction [tx ds]
          (some->> (record-facts ds facts tx-time)
                   (record-transaction db tx-time))))))

  (obliterate [this id]))                                 ;; "big brother" move: idenitity never existed

(defn connect
  ([ds]
   (connect ds "public"))
  ([ds schema]
   (->Postgres ds
               schema
               (v/load-queries "postgres"))))

(defn create-institute-of-time
  ([ds]
   (create-institute-of-time ds "public"))
  ([ds schema]
   (let [sql (-> (v/load-queries "postgres")
                 :create-institute-of-time
                 (q/with-params {:schema {:as schema}}))]
     (with-open [conn (jdbc/get-connection ds)]
       (jdbc/execute! conn [sql])))))
