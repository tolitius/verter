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
    (hk/make-datasource datasource)))

(defn stop-db [datasource]
 (hk/close-datasource datasource))

(defn execute-sql! [datasource sql]
  (with-open [conn (jdbc/get-connection datasource)]
    (jdbc/execute! conn [sql])))

; $ clojure -A:dev -A:repl

; (require 'dev)(in-ns 'dev)
; (def db (start-db))
; (def v (vp/connect db))

; (vc/add-facts v [{:id :foo/bar/v1 :zoo 34} [{:id :foo/bar/v2 :baz 35} #inst "2019-09-09"]])
; (vc/facts v :foo/bar/v2)

; (stop-db db)
