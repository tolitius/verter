(ns verter.test
  (:require [clojure.test :as t :refer [deftest is]]
            [verter.test.tools :as tt]
            [verter.core :as v]
            [verter.store :as vs]
            [verter.family :as vf]))

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

(deftest should-rollup
  (v/add-facts tt/conn [{:verter/id :universe/sixty-six :suns 42 :planets #{:and-all :earth}, :life? true}
                        {:verter/id :universe/sixty-six :moons 42}
                        {:verter/id :universe/sixty-six :moons nil}])
  (is (= {:suns 42,
          :planets #{:and-all :earth},
          :life? true,
          :verter/id :universe/sixty-six}

         (tt/without-ts
           (v/rollup tt/conn :universe/sixty-six))))

  (is (= {:suns 42,
          :planets #{:and-all :earth},
          :life? true,
          :verter/id :universe/sixty-six,
          :moons nil}

         (tt/without-ts
           (v/rollup tt/conn :universe/sixty-six
                     {:with-nils? true})))))

(deftest should-add-children
  (vf/add-child tt/conn
                :universe/one
                :star-systems
                {:verter/id :star-system/alpha
                 :stars 3
                 :planets 2})

  (vf/add-child tt/conn
                :universe/one
                :star-systems
                {:verter/id :star-system/beta
                 :stars 2
                 :planets 1})

  (is (= {:star-system/alpha {:verter/id :star-system/alpha
                              :stars 3
                              :planets 2}
          :star-system/beta {:verter/id :star-system/beta
                             :stars 2
                             :planets 1}}

         (tt/without-ts
           (vf/find-children tt/conn :universe/one :star-systems)))))

(deftest should-add-many-children-at-once
  (vf/add-children tt/conn
                   :universe/one
                   :star-systems
                   [{:verter/id :star-system/gamma
                     :stars 5
                     :planets 8}
                    {:verter/id :star-system/delta
                     :stars 1
                     :planets 3}
                    {:verter/id :star-system/epsilon
                     :stars 2
                     :planets 0}])

  (is (= {:star-system/gamma {:verter/id :star-system/gamma
                              :stars 5
                              :planets 8}
          :star-system/delta {:verter/id :star-system/delta
                              :stars 1
                              :planets 3}
          :star-system/epsilon {:verter/id :star-system/epsilon
                                :stars 2
                                :planets 0}}

         (tt/without-ts
          (vf/find-children tt/conn :universe/one :star-systems)))))

(deftest should-find-children-with-deleted
  (vf/add-child tt/conn
                :universe/one
                :star-systems
                {:verter/id :star-system/alpha
                 :stars 3})

  (vf/add-child tt/conn
                :universe/one
                :star-systems
                {:verter/id :star-system/beta
                 :stars 2})

  (vf/remove-child tt/conn
                   :universe/one
                   :star-systems
                   :star-system/alpha)

  (is (= {:star-system/beta {:verter/id :star-system/beta
                             :stars 2}}

         (tt/without-ts
           (vf/find-children tt/conn :universe/one :star-systems))))

  (is (= {:star-system/alpha {:verter/id :star-system/alpha
                              :stars 3
                              :deleted? true}
          :star-system/beta {:verter/id :star-system/beta
                             :stars 2}}

         (tt/without-ts
           (vf/find-children tt/conn
                            :universe/one
                            :star-systems
                            {:with-deleted? true})))))

(deftest should-find-children-at-time
  (vf/add-child tt/conn
                :universe/one
                :star-systems
                {:verter/id :star-system/alpha
                 :stars 3})

  (let [t1 (java.time.Instant/now)]
    (Thread/sleep 10)

    (vf/add-child tt/conn
                  :universe/one
                  :star-systems
                  {:verter/id :star-system/beta
                   :stars 2})

    (is (= {:star-system/alpha {:verter/id :star-system/alpha
                                :stars 3}}

           (tt/without-ts
             (vf/find-children-at tt/conn :universe/one :star-systems t1))))

    (is (= {:star-system/alpha {:verter/id :star-system/alpha
                                :stars 3}
            :star-system/beta {:verter/id :star-system/beta
                               :stars 2}}

           (tt/without-ts
             (vf/find-children-at tt/conn
                                 :universe/one
                                 :star-systems
                                 (java.time.Instant/now)))))))
