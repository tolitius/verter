(ns verter.core)

(defprotocol Identity
  (now [this id])                ;; rollup of facts for this identity up until now
  (as-of [this id ts])           ;; rollup of facts for this identity up until a timestamp
  (facts [this id])              ;; all the facts ever added in order
  (add-facts [this id facts])    ;; add one or more facts
  (obliterate [this id]))        ;; "big brother" move: idenitity never existed
