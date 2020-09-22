(ns verter.tools
  (:require [cljhash.core :as hasher]
            [clojure.string :as s]
            [calip.core :as calip]
            [taoensso.nippy :as nippy])
  (:import [com.google.common.hash Hashing
                                   Funnel
                                   PrimitiveSink]))

(defn measure-it
  ([fns]
   (measure-it fns nil))
  ([fns log]
   (let [log (or log println)]
     (calip/measure fns
                    {:report (fn [{:keys [took fname]}]
                               (log (format "%s took %,d ns" fname took)))}))))

(defn unmeasure-it [fns]
  (calip/uncalip fns))

(def nippy-funnel
  (reify Funnel
    (^void funnel [_ obj ^PrimitiveSink sink]
      (.putBytes sink (nippy/freeze obj)))))

(defn hash-it [obj]
  (let [murmur (Hashing/murmur3_128)
        h (try
            (hasher/clj-hash murmur obj)
            (catch Exception e
              (hasher/hash-obj murmur nippy-funnel obj)))]
    (str h)))

(defn now []
  (java.time.Instant/now))

(defn ts->date [ts]
  (when (number? ts)
    (java.util.Date. ts)))

(defn inst->date [in]
  (java.util.Date/from in))

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

(defn to-multi-queries [q]
  (s/split q #"--;;"))

(defn squuid []
  (let [uuid (java.util.UUID/randomUUID)
        time (System/currentTimeMillis)
        secs (quot time 1000)
        lsb (.getLeastSignificantBits uuid)
        msb (.getMostSignificantBits uuid)
        timed-msb (bit-or (bit-shift-left secs 32)
                          (bit-and 0x00000000ffffffff msb))]
    (java.util.UUID. timed-msb lsb)))
