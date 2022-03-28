(ns verter.test
  (:require [clojure.test :as t :refer [deftest is]]
            [verter.test.tools :as tt]
            [verter.core :as v]
            [verter.store :as vs]))

(t/use-fixtures :each (partial tt/with-db "multiverse"))

(deftest should-connect-to-db
  (is (tt/is-connected tt/conn)))

(deftest should-record-facts
  (v/add-facts tt/conn [{:verter/id :universe/one :suns 12 :planets #{:one :two :three}}
                        [{:verter/id :universe/two :suns 3 :life? true} #inst "2019-09-09"]
                        {:verter/id :universe/sixty-six :answer 42}])
  (v/add-facts tt/conn [{:verter/id :universe/one :suns 42 :planets #{:one :two :three}}
                        [{:verter/id :universe/two :suns 3 :life? true} #inst "2020-09-09"]
                        {:verter/id :universe/sixty-six :answer 42}])
  (is (= [{:suns 12,
           :planets #{:one :three :two},
           :verter/id :universe/one}
          {:suns 42,
           :planets #{:one :three :two},
           :verter/id :universe/one}]

         (tt/without-ts
           (v/facts tt/conn :universe/one)))))
