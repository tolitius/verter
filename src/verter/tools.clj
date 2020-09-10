(ns verter.tools
  (:require [æsahættr :as hasher]))

(defn hash-it [obj]
  (let [murmur (hasher/murmur3-128)
        h (try
            (hasher/hash-object murmur obj)
            (catch Exception e
              (hasher/hash-object murmur hasher/nippy-funnel obj)))]
    (str h)))

(defn now []
  (java.time.Instant/now))
