(ns verter.core
  (:require [inquery.core :as q]
            [taoensso.nippy :as nippy]
            [clojure.edn :as edn]
            [verter.tools :as vt]))

(defprotocol Identity
  (now [this id])                ;; rollup of facts for this identity up until now
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
                     :obliterate-identity]
                    {:path (str "verter/store/" store)}))

(defn to-row [tx-time fact]
  (let [[{:keys [id] :as fact} at] (cond (map? fact) [fact tx-time]
                                         (sequential? fact) fact
                                         :else (throw (ex-info "a fact can be either a map or a vector with a map and an #inst"
                                                               {:fact fact})))
        fval (dissoc fact :id)]
    [(str id)
     (nippy/freeze fval)
     (vt/hash-it fval)
     at]))

(defn from-row [{:keys [key value at]}]
  (-> value
      nippy/thaw
      (assoc :id (edn/read-string key)
             :at at)))

