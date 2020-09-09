(ns verter.store.postgres
  (:require [next.jdbc :as jdbc]
            [verter.core :as v]
            [inquery.core :as q]))

(defrecord Postgres [ds]
  v/Identity
  (now [this id])                    ;; rollup of facts for this identity
  (as-of [this id ts])               ;; rollup of facts for this identity up until a timestamp
  (facts [{:keys [ds]} id]           ;; all the facts ever added in order
    ())
  (add-facts [this id facts])        ;; add one or more facts
  (obliterate [this id]))            ;; "big brother" move: idenitity never existed

(defn connect [ds]
  (->Postgres ds))

(defn create-institute-of-time
  ([ds]
   (create-institute-of-time ds "public"))
  ([ds schema]
   (let [sql (-> (q/make-query-map [:create-institute-of-time]
                                   {:path "verter/store/postgres"})
                 :create-institute-of-time
                 (q/with-params {:schema {:as schema}}))]
     (with-open [conn (jdbc/get-connection ds)]
       (jdbc/execute! conn [sql])))))
