(ns dev
 (:require [hikari-cp.core :as hk]
           [inquery.core :as q]
           [cprop.core :as c]
           [next.jdbc :as jdbc]
           [next.jdbc.result-set :as rs]
           [verter.core :as v]
           [verter.store.postgres :as vp]
           [clojure.repl :as repl]
           [clojure.pprint :as pp]))

(defn start-db []
  (println "[verter] connecting to the database..")
  (let [{:keys [datasource]} (-> (c/load-config)
                                 :verter)]
    (hk/make-datasource datasource)))

(defn stop-db [datasource]
 (hk/close-datasource datasource))

(defn execute-sql! [datasource sql]
  (with-open [conn (jdbc/get-connection datasource)]
    (jdbc/execute! conn [sql]
                   {:return-keys true
                    :builder-fn rs/as-unqualified-lower-maps})))

; $ clojure -A:dev -A:repl

; (require 'dev)(in-ns 'dev)
; (def db (start-db))
; (def verter (vp/connect db))

; (v/add-facts verter [{:id :universe/one :suns 12 :planets #{:one :two :three}}
;                      [{:id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
;                      {:id :universe/sixty-six :answer 42}])

; (v/facts verter :universe/two)

; (stop-db db)
