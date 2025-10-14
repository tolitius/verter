(ns verter.test.tools
  (:require [next.jdbc :as jdbc]
            [hikari-cp.core :as hk]
            [clojure.java.io :as io]
            [verter.store :as vs])
  (:import [com.zaxxer.hikari.pool HikariProxyConnection]))

(defn make-db [db-name]
  (hk/make-datasource {:jdbc-url (str "jdbc:sqlite:/tmp/" db-name ".db")}))

(def ^:dynamic conn nil)

(defn connect [db-name]
  (->> db-name
       make-db
       (vs/connect :sqlite)))

(defn clean-db [{:keys [ds]}]
  (let [drop-it [["drop table if exists facts;"]
                 ["drop table if exists transactions;"]]]
    (with-open [conn (jdbc/get-connection ds)]
      (mapv #(jdbc/execute! conn %)
            drop-it))))

(defn with-db [db-name f]
  (binding [conn (connect db-name)]
    (clean-db conn)
    (vs/create-institute-of-time :sqlite
                                 (:ds conn))
    (f)
    (clean-db conn)
    (hk/close-datasource (:ds conn))))

(defn is-connected [{:keys [ds]}]
  (->> ds
       .getConnection
       (instance? HikariProxyConnection)))

(defn without-ts [data]
  (cond
    (map? data)
    (into {} (map (fn [[k v]]
                   [k (if (= k :at)
                        nil
                        (without-ts v))])
                 (dissoc data :at)))

    (sequential? data)
    (mapv without-ts data)

    :else data))
