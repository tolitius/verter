(ns verter.java
  (:require [verter.core :as v]
            [verter.store :as vs]
            [clojure.edn :as edn])
  (:import [java.util List Map]
           [javax.sql DataSource]
           [java.time Instant]))

(gen-class
  :name tolitius.Verter
  :methods [^{:static true} [connect [String javax.sql.DataSource] verter.core.Identity]
            ^{:static true} [addFacts [verter.core.Identity java.util.List] java.util.List]
            ^{:static true} [facts [verter.core.Identity String] java.util.List]
            ; ^{:static true} [facts [verter.core.Identity String java.time.Instant] java.util.List]
            ; ^{:static true} [rollup [verter.core.Identity String] java.util.Map]
            ; ^{:static true} [asOf [verter.core.Identity String java.time.Instant] java.util.Map]
            ])

;; Java friendly faces

(defn -connect [dbtype datasource]
  (vs/connect (keyword dbtype)
              datasource))

(defn -addFacts [verter facts]
  (v/add-facts verter facts))

(defn -facts [verter id]
  (v/facts verter
           (edn/read-string id)))
