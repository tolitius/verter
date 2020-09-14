(ns verter.store
  (:require [verter.store.postgres :as vp]
            ; [verter.store.mysql :as vm]
            ; [verter.store.oracle :as vo]
            ; [verter.store.mssql :as vms]
            ; [verter.store.redis :as vr]
            ; [verter.store.cassandra :as vc]
            ; [verter.store.couchdb :as vcb]
            ;; ...
            ))

(defn- not-yet [dbtype]
  (throw (ex-info (str "verter don't have support for the " dbtype
                       " data store... yet. but, but, you are very welcome to add this support."
                       " take a look at https://github.com/tolitius/verter#add-data-store")
                  {:dbtype dbtype})))

;; handrolled mutimethod: easier to navigate, follow and reason about
(defn connect [dbtype datasource]
  (case dbtype
    :postgres (vp/connect datasource)
    (not-yet dbtype)))
