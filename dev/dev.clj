(ns dev
 (:require [hikari-cp.core :as hk]
           [inquery.core :as q]
           [cprop.core :as c]
           [next.jdbc :as jdbc]
           [next.jdbc.result-set :as rs]
           [verter.core :as vc]
           [verter.store.postgres :as vp]
           [yang.lang :as y]))

(defn start-db []
  (println "[verter] connecting to the database..")
  (let [{:keys [datasource queries]} (-> (c/load-config)
                                         :verter)]
    {:queries    (q/make-query-map queries
                                   {:path "verter/store/postgres"})
     :datasource (hk/make-datasource datasource)}))

(defn stop-db [{:keys [datasource]}]
 (hk/close-datasource datasource))

(defn execute-sql! [{:keys [datasource]} sql]
  (with-open [conn (jdbc/get-connection datasource)]
    (jdbc/execute! conn [sql])))

; (require 'dev)(in-ns 'dev)
; (def db (start-db))
; (def v (vp/connect (:datasource db)))

; (vc/facts v :foo/bar/v2)
; (vc/add-facts v [{:id :foo/bar/v1 :zoo 34} [{:id :foo/bar/v2 :baz 35} #inst "2019-09-09"]])
