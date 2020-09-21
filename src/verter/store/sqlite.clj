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

(defn- make-insert-facts-batch-query [facts]
  (let [rows (mapv v/to-fact-row facts)
        [qhead & data] (qb/for-insert-multi :facts
                                            [:key :value :hash]
                                            rows
                                            {})
        qfoot " on conflict do nothing"]
    (concat [(str qhead qfoot)] data)))

(defn- make-insert-txs-batch-query [facts]
  (let [tx-time (vt/now)
        tx-id (vt/squuid)
        rows (mapv (partial v/to-tx-row tx-id tx-time)
                   facts)
        [qhead & data] (qb/for-insert-multi :transactions
                                            [:id :hash :business_time :at]
                                            rows
                                            {})
        qfoot " on conflict do nothing"]
    [tx-id tx-time (concat [(str qhead qfoot)] data)]))

(defn- record-transaction [{:keys [ds queries]}
                           facts]
  (let [[tx-id tx-time sql] (make-insert-txs-batch-query facts)
        recorded (jdbc/execute! ds sql)]
    (if (= (-> recorded first :next.jdbc/update-count) 0)
      (let [noop (str "no changes to identities were detected, and hence, these facts " facts " were NOT added at " tx-time)]
        (println noop)
        {:noop noop})
      {:tx-id tx-id})))

(defn- record-facts [{:keys [ds]}
                     facts]
  (let [sql (make-insert-facts-batch-query facts)]
    (jdbc/execute! ds sql
                   {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})))

(defn- update-insts [{:keys [at tx-time] :as fact}]
  (let [f (if-not tx-time
            fact
            (update fact :tx-time vt/ts->date))]
    (update f :at vt/ts->date)))

(defn- find-facts
  "find all the facts about identity upto a certain time"
  [{:keys [ds schema queries]}
   id
   {:keys [upto]
    :or {upto (vt/now)}
    :as opts}]
  (let [sql (-> queries
                :find-facts-up-to
                (q/with-params {:key (str id)}))]
    (with-open [conn (jdbc/get-connection ds)]
      (->> (jdbc/execute! conn [sql upto]
                          {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
           (mapv (comp
                   update-insts
                   (partial v/from-row opts)))))))

(defrecord Sqlite [ds queries]
  v/Identity

  (facts [this id]                                        ;; find facts up until now
     (find-facts this id {}))

  (facts [this id opts]                                   ;; find facts with options
     (find-facts this id opts))

  (add-facts [{:keys [ds] :as db} facts]                  ;; add one or more facts
    (when (seq facts)
      (jdbc/with-transaction [tx ds {:read-only false}]
        (let [with-tx (assoc db :ds tx)]
          (record-facts with-tx facts)
          (record-transaction with-tx facts)))))

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
