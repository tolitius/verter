(ns verter.core
  (:require [inquery.core :as q]))

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

