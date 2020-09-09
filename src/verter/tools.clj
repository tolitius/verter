(ns verter.tools
  (:require [æsahættr :as hasher]))

(defn hash-it [obj]
  (->> obj
       (hasher/hash-object (hasher/murmur3-128))
       str))
