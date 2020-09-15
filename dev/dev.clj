(ns dev
 (:require [hikari-cp.core :as hk]
           [inquery.core :as q]
           [cprop.core :as c]
           [next.jdbc :as jdbc]
           [next.jdbc.result-set :as rs]
           [verter.core :as v]
           [verter.store :as vs]
           [verter.store.postgres :as vp]
           [calip.core :as calip]
           [clojure.repl :as repl]
           [clojure.pprint :as pp :refer [pprint]]))

(defn start-db
  ([]
   (start-db :postgres))
  ([dbtype]
   (println "[verter] connecting to the" dbtype "database..")
   (let [{:keys [datasource]} (-> (c/load-config)
                                  :verter
                                  :store
                                  dbtype)]
     (hk/make-datasource datasource))))

(defn stop-db [datasource]
 (hk/close-datasource datasource))

(defn execute-sql! [datasource sql]
  (with-open [conn (jdbc/get-connection datasource)]
    (jdbc/execute! conn [sql]
                   {:return-keys true
                    :builder-fn rs/as-unqualified-lower-maps})))

;; performance corner

(def to-measure #{#'verter.store.postgres/make-insert-batch-query
                   #'verter.store.postgres/record-transaction
                   #'verter.store.postgres/record-facts
                   #'next.jdbc/execute!})

(defn measure-it []
  (calip/measure to-measure
                 {:report (fn [{:keys [took fname]}]
                            (println (format "%s took %,d ns" fname took)))}))

(defn unmeasure-it []
  (calip/uncalip to-measure))

; $ clojure -A:dev -A:repl

; (require 'dev)(in-ns 'dev)
; (def db (start-db))

; ; (vp/create-institute-of-time db) ;; only if it is not already created

; (def verter (vs/connect :postgres db))

; (v/add-facts verter [{:verter/id :universe/one :suns 12 :planets #{:one :two :three}}
;                      [{:verter/id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
;                      {:verter/id :universe/sixty-six :answer 42}])

; (v/facts verter :universe/two)

; (stop-db db)
