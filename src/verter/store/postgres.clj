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

(defn- make-insert-facts-batch-query [schema facts]
  (let [rows (mapv v/to-fact-row facts)
        [qhead & data] (qb/for-insert-multi (str schema ".facts")
                                            [:key :value :hash]
                                            rows
                                            {})
        qfoot " on conflict on constraint fact_uq
                            do nothing"]
    (concat [(str qhead qfoot)] data)))

(defn- make-insert-txs-batch-query [schema facts]
  (let [tx-time (vt/now)
        tx-id (vt/squuid)
        rows (mapv (partial v/to-tx-row tx-id tx-time)
                   facts)
        [qhead & data] (qb/for-insert-multi (str schema ".transactions")
                                            [:id :hash :business_time :at]
                                            rows
                                            {})
        qfoot " on conflict on constraint business_hash_uq
                            do nothing"]
    [tx-id tx-time (concat [(str qhead qfoot)] data)]))

(defn- record-transaction [{:keys [ds schema queries]}
                           facts]
  (let [[tx-id tx-time sql] (make-insert-txs-batch-query schema facts)
        recorded (jdbc/execute! ds sql)]
    (if (= (-> recorded first :next.jdbc/update-count) 0)
      (let [noop (str "no changes to identities were detected, and hence, these facts " facts " were NOT added at " tx-time)]
        (println noop)
        {:noop noop})
      {:tx-id tx-id})))

(defn- record-facts [{:keys [ds schema]}
                     facts]
  (let [sql (make-insert-facts-batch-query schema facts)]
    (jdbc/execute! ds sql
                   {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})))

(defn- find-facts
  "find all the facts about identity upto a certain time"
  [{:keys [ds schema queries]}
   id
   {:keys [upto]
    :or {upto (vt/now)}
    :as opts}]
  (let [sql (-> queries
                :find-facts-up-to
                (q/with-params {:key (str id)
                                :schema {:as schema}}))]
    (with-open [conn (jdbc/get-connection ds)]
      (->> (jdbc/execute! conn [sql upto]
                          {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
           (mapv (partial v/from-row opts))))))

(defrecord Postgres [ds outer-tx? schema queries]
  v/Identity

  (facts [this id]                                        ;; find facts up until now
     (find-facts this id {}))

  (facts [this id opts]                                   ;; find facts with options
     (find-facts this id opts))

  (add-facts [{:keys [ds] :as db} facts]                  ;; add one or more facts
    (when (seq facts)
      (if-not outer-tx?
        (jdbc/with-transaction [tx ds {:read-only false}]
          (let [with-tx (assoc db :ds tx)]
            (record-facts with-tx facts)
            (record-transaction with-tx facts)))
        (do
          (record-facts db facts)
          (record-transaction db facts)))))

  (obliterate [this id]))                                 ;; "big brother" move: idenitity never existed

(defn connect
  ([ds]
   (connect ds {}))
  ([ds {:keys [schema outer-tx?]
        :or {schema "public"
             outer-tx? false}}]
   (->Postgres ds
               outer-tx?
               schema
               (-> "postgres"
                   v/load-queries
                   (dissoc :create-institute-of-time)))))

(defn create-institute-of-time
  ([ds]
   (create-institute-of-time ds {}))
  ([ds {:keys [schema]
        :or {schema "public"}}]
   (let [sql (-> (v/load-queries "postgres")
                 :create-institute-of-time
                 (q/with-params {:schema {:as schema}}))]
     (with-open [conn (jdbc/get-connection ds)]
       (jdbc/execute! conn [sql])))))

;; performance corner

(def to-measure #{#'verter.store.postgres/make-insert-facts-batch-query
                  #'verter.store.postgres/make-insert-txs-batch-query
                  #'verter.store.postgres/record-transaction
                  #'verter.store.postgres/record-facts
                  #'next.jdbc/execute!})
