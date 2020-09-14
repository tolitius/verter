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

(defn remove-nil-vals [m]
  (->> (for [[k v] m]
         (when-not (nil? v)
           [k v]))
       (into {})))

(defn fmk
  "apply f to each key k of map m"
  [m f]
  (into {}
        (for [[k v] m]
          [(f k) v])))

(defn value? [v]
  (or (number? v)
      (keyword? v)
      (seq v)))
