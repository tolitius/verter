(ns verter.java
  (:require [verter.core :as v]
            [verter.store :as vs]
            [verter.tools :as vt]
            [clojure.edn :as edn])
  (:import [verter.core Identity]
           [java.util List Map]
           [javax.sql DataSource]
           [java.time Instant]))

(gen-class
  :name tolitius.Verter
  :methods [^{:static true} [connect [String javax.sql.DataSource] verter.core.Identity]
            ^{:static true} [connect [String javax.sql.DataSource java.util.Map] verter.core.Identity]
            ^{:static true} [createInstituteOfTime [String javax.sql.DataSource] void]
            ^{:static true} [createInstituteOfTime [String javax.sql.DataSource java.util.Map] void]
            ^{:static true} [addFacts [verter.core.Identity java.util.List] java.util.List]
            ^{:static true} [facts [verter.core.Identity String] java.util.List]
            ; ^{:static true} [facts [verter.core.Identity String java.time.Instant] java.util.List]
            ; ^{:static true} [rollup [verter.core.Identity String] java.util.Map]
            ; ^{:static true} [asOf [verter.core.Identity String java.time.Instant] java.util.Map]
            ])

;; Java friendly faces

(defn -createInstituteOfTime
  ([dbtype datasource]
   (-createInstituteOfTime dbtype datasource {}))
  ([dbtype datasource opts]
   (vs/create-institute-of-time (keyword dbtype)
                                datasource
                                (vt/fmk opts keyword))))

(defn -connect
  ([dbtype datasource]
   (-connect dbtype datasource {}))
  ([dbtype datasource opts]
   (vs/connect (keyword dbtype)
               datasource
               (vt/fmk opts keyword))))

(defn -addFacts [verter facts]
  (v/add-facts verter facts))

(defn -facts [verter id]
  (v/facts verter
           (edn/read-string id)))
