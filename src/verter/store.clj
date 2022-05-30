(ns verter.store
  (:require [verter.store.postgres :as vp]
            [verter.store.sqlite :as vsl]
            ; [verter.store.mysql :as vm]
            ; [verter.store.oracle :as vo]
            ; [verter.store.mssql :as vms]
            ; [verter.store.redis :as vr]
            ; [verter.store.cassandra :as vc]
            ; [verter.store.couchbase :as vcb]
            ;; ...
            [verter.tools :as vt]
            ))

(defn- not-yet [dbtype]
  (throw (ex-info (str "verter don't have support for the " dbtype
                       " data store... yet. but, but, you are very welcome to add this support."
                       " take a look at https://github.com/tolitius/verter#add-data-store")
                  {:dbtype dbtype})))

(defn- with-metrics [{:keys [measure? log]} fns]
  (when measure?
    (vt/measure-it fns log)))

;; handrolled mutimethods: easier to navigate, follow and reason about
;; iff more sophistication arrives, use a Store connect/create-institute-of-time protocol

(defn connect
  ([dbtype datasource]
   (connect dbtype datasource {}))
  ([dbtype datasource opts]
   (case dbtype
     :postgres (do
                 (with-metrics opts vp/to-measure)
                 (vp/connect datasource opts))
     :sqlite (do
                 (with-metrics opts vsl/to-measure)
                 (vsl/connect datasource opts))
     (not-yet dbtype))))

(defn create-institute-of-time
  ([dbtype datasource]
   (create-institute-of-time dbtype
                             datasource
                             {}))
  ([dbtype datasource opts]
   (case dbtype
     :postgres (vp/create-institute-of-time datasource opts)
     :sqlite (vsl/create-institute-of-time datasource opts)
     (not-yet dbtype))))

(defn within-tx
  ;; for now applies to all stores, but could be store specific in the future
  [store tx]
  (assoc store
         :ds tx
         :outer-tx? true))
