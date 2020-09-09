(ns verter.store.postgres
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :as qb]
            [verter.core :as v]
            [verter.tools :as vt]
            [taoensso.nippy :as nippy]
            [inquery.core :as q]))

(defn- make-insert-batch-query [facts]
  (let [rows (->> (for [[{:keys [id] :as fact} at] facts]
                    (let [value (dissoc fact :id)]
                      [(str id)
                       (nippy/freeze value)
                       (vt/hash-it value)
                       at]))
                  (into []))
        [qhead & data] (qb/for-insert-multi :facts
                                            [:key :value :hash :at]
                                            rows
                                            {})
        qfoot " on conflict on constraint fact_uq
                            do nothing"]
    (concat [(str qhead qfoot)] data)))

(defrecord Postgres [ds]
  v/Identity

  (now [this id])                         ;; rollup of facts for this identity

  (as-of [this id ts])                    ;; rollup of facts for this identity up until a timestamp

  (facts [{:keys [ds queries]} id]        ;; all the facts ever added in order
    (let [sql (-> queries
                  :find-facts-by-key
                  (q/with-params {:id id}))]
      (with-open [conn (jdbc/get-connection ds)]
        (jdbc/execute! conn [sql]))))

  (add-facts [{:keys [ds]} facts]         ;; add one or more facts
    (when (seq facts)
      (let [query (make-insert-batch-query facts)]
        (jdbc/with-transaction [tx ds]
          (jdbc/execute! ds query)
          ;; TODO: add one insert into transactions table
          ))))

  (obliterate [this id]))                 ;; "big brother" move: idenitity never existed

(defn connect [ds]
  (->Postgres {:ds ds
               :queries (v/load-queries "postgres")}))

(defn create-institute-of-time
  ([ds]
   (create-institute-of-time ds "public"))
  ([ds schema]
   (let [sql (-> (v/load-queries "postgres")
                 :create-institute-of-time
                 (q/with-params {:schema {:as schema}}))]
     (with-open [conn (jdbc/get-connection ds)]
       (jdbc/execute! conn [sql])))))
