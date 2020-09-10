(ns verter.store.postgres
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :as qb]
            [next.jdbc.date-time]
            [next.jdbc.result-set :as jdbcr]
            [clojure.edn :as edn]
            [verter.core :as v]
            [verter.tools :as vt]
            [taoensso.nippy :as nippy]
            [inquery.core :as q]))

(defn- to-row [tx-time fact]
  (let [[{:keys [id] :as fact} at] (cond (map? fact) [fact tx-time]
                                         (sequential? fact) fact
                                         :else (throw (ex-info "a fact can be either a map or a vector with a map and an #inst"
                                                               {:fact fact})))
        fval (dissoc fact :id)]
    [(str id)
     (nippy/freeze fval)
     (vt/hash-it fval)
     at]))

(defn- make-insert-batch-query [facts tx-time]
  (let [rows (mapv (partial to-row tx-time)
                   facts)
        [qhead & data] (qb/for-insert-multi :facts
                                            [:key :value :hash :at]
                                            rows
                                            {})
        qfoot " on conflict on constraint fact_uq
                            do nothing"]
    (concat [(str qhead qfoot)] data)))

(defn- from-row [{:keys [key value at]}]
  (-> value
      nippy/thaw
      (assoc :id (edn/read-string key)
             :at at)))

(defrecord Postgres [ds schema queries]
  v/Identity

  (now [this id])                                   ;; rollup of facts for this identity

  (as-of [this id ts])                              ;; rollup of facts for this identity up until a timestamp

  (facts [{:keys [ds schema queries]} id]           ;; all the facts ever added in order
    (let [sql (-> queries
                  :find-facts-by-key
                  (q/with-params {:key (str id)
                                  :schema {:as schema}}))]
      (with-open [conn (jdbc/get-connection ds)]
        (mapv from-row (jdbc/execute! conn [sql]
                                      {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})))))

  (add-facts [{:keys [ds schema]} facts]            ;; add one or more facts
    (when (seq facts)
      (let [query (make-insert-batch-query facts
                                           (vt/now))] ;; tx time
        (jdbc/with-transaction [tx ds]
          (jdbc/execute! ds query
                         {:return-keys true :builder-fn jdbcr/as-unqualified-lower-maps})
          ;; TODO: add one insert into transactions table
          ))))

  (obliterate [this id]))                           ;; "big brother" move: idenitity never existed

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
