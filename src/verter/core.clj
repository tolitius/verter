(ns verter.core
  (:require [inquery.core :as q]
            [taoensso.nippy :as nippy]
            [clojure.edn :as edn]
            [verter.tools :as vt]))

(defprotocol Identity
  (facts [this id]
         [this id opts]
    "find all the facts about the identity until now or with options")

  (add-facts [this facts]
    "add one or more facts with or without time specified
     if time is not specified, a single transaction time is used")

  (obliterate [this id]
     "'big brother' move: the idenitity never existed"))

(defn load-queries [store]
  (q/make-query-map [:create-institute-of-time
                     :record-transaction
                     :find-facts-up-to
                     :add-facts
                     :obliterate-identity]
                    {:path (str "verter/store/" store)}))

(defn validate-fact
  ;; will have more and most like will use metosin/malli
  [{:keys [verter/id] :as fact}]
  (when-not (vt/value? id)
    (throw (ex-info (str "a fact should have a \":verter/id\" key with a non empty value "
                         "in order to uniquely \"identify the identity\" this fact belongs to")
                    {:fact fact}))))

(defn- normalize-fact
  ([fact]
   (normalize-fact :noop fact))
  ([tx-time fact]
   (cond (map? fact) [fact tx-time]
         (sequential? fact) fact
         :else (throw (ex-info "a fact can be either a map or a vector with a map and an #inst"
                               {:fact fact})))))

(defn to-fact-row [fact]
  (let [[{:keys [verter/id] :as fact} _] (normalize-fact fact)
        _ (validate-fact fact)
        fval (dissoc fact :verter/id)]
    [(str id)
     (nippy/freeze fval)
     (vt/hash-it fact)]))

(defn to-tx-row [tx-id tx-time fact]
  (let [[{:keys [verter/id] :as fact} at] (normalize-fact tx-time fact)
        business-time (or at tx-time)]
    [tx-id
     (vt/hash-it fact)
     business-time
     tx-time]))

(defn from-row [{:keys [with-tx?]
                 :or {with-tx? false}}
                {:keys [key value business_time tx_time tx_id]}]
  (let [v (nippy/thaw value)
        fact (assoc v :verter/id (edn/read-string key)
                      :at business_time)]
    (if-not with-tx?
      fact
      (assoc fact
             :tx-time tx_time
             :tx-id tx_id))))

(defn- merge-asc [facts]
  (->> facts
       (apply merge)
       vt/remove-nil-vals))

(defn as-of
  "rollup of facts for this identity up until / as of a given time"
  [db id ts]
  (-> (facts db id {:upto ts})
      merge-asc))

(defn rollup
  "rollup of facts for this identity up until now"
  [db id]
  (-> (facts db id)
      merge-asc))
