(ns verter.core
  (:require [inquery.core :as q]
            [taoensso.nippy :as nippy]
            [clojure.edn :as edn]
            [verter.tools :as vt]))

(defprotocol Identity
  (now [this id])
  (as-of [this id ts])           ;; rollup of facts for this identity up until a timestamp
  (facts [this id])              ;; all the facts ever added in order
  (add-facts [this facts])       ;; add one or more facts
  (obliterate [this id]))        ;; "big brother" move: idenitity never existed

(defn load-queries [store]
  (q/make-query-map [:create-institute-of-time
                     :find-identity-by-key
                     :find-facts-by-key
                     :find-facts-as-of
                     :add-facts
                     :record-transaction
                     :obliterate-identity]
                    {:path (str "verter/store/" store)}))

(defn to-row [tx-time fact]
  (let [[{:keys [id] :as fact} at] (cond (map? fact) [fact tx-time]
                                         (sequential? fact) fact
                                         :else (throw (ex-info "a fact can be either a map or a vector with a map and an #inst"
                                                               {:fact fact})))
        fval (dissoc fact :id)
        to-hash (if (= at tx-time)
                  fval              ;; if no business time provided, hash only the fact value
                  [fval (str at)])] ;; if business time provided, include it in a hash to make sure to record if it changed
    [(str id)
     (nippy/freeze fval)
     (vt/hash-it to-hash)
     at]))

(defn from-row [{:keys [key value at]}]
  (-> value
      nippy/thaw
      (assoc :id (edn/read-string key)
             :at at)))

(defn- merge-asc [facts]
  (->> facts
       (apply merge)
       vt/remove-nil-vals))

(defn rollup
  "rollup of facts for this identity up until now"
  [db id]
  (-> (facts db id)
      merge-asc))
