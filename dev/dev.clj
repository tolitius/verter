(ns dev
 (:require [hikari-cp.core :as hk]
           [inquery.core :as q]
           [cprop.core :as c]
           [next.jdbc :as jdbc]
           [next.jdbc.result-set :as rs]
           [verter.core :as v]
           [verter.store :as vs]
           [verter.store.postgres :as vp]
           [verter.tools :as vt]
           [clojure.repl :as repl]
           [clojure.pprint :as pp :refer [pprint]]))

(defn start-db
  ([]
   (start-db :postgres))
  ([dbtype]
   (println "[verter] connecting to the" dbtype "database..")
   (let [{:keys [datasource]} (-> (c/load-config)              ;; reads "config.edn" from a classpath
                                  :verter
                                  :store
                                  dbtype)]
     (hk/make-datasource datasource))))

(defn stop-db [datasource]
 (hk/close-datasource datasource))

; $ clojure -A:dev -A:repl

; (require 'dev)(in-ns 'dev)
; (def db (start-db))                ;; will create a datasource to a local postgres by default

; ; (vp/create-institute-of-time db) ;; only iff it is not already created

; (def verter (vs/connect :postgres db))

; (v/add-facts verter [{:verter/id :universe/one :suns 12 :planets #{:one :two :three}}
;                      [{:verter/id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
;                      {:verter/id :universe/sixty-six :answer 42}])

; (v/facts verter :universe/two)

; (stop-db db)
