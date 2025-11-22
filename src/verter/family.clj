(ns verter.family
  (:require [verter.core :as v]))

(defn- family-id
  "create family identity for a specific child type
   e.g. :universe/andromeda + :star-systems -> :universe/andromeda.star-systems"
  [parent-id child-type]
  (keyword (namespace parent-id)
           (str (name parent-id) "." (name child-type))))

(defn- validate-child-type [child-type]
  (when-not (and (keyword? child-type)
                 (not (namespace child-type)))
    (throw (ex-info "child-type must be a non-namespaced keyword"
                    {:child-type child-type
                     :example :star-systems}))))

(defn add-child
  "add child to parent's family of given type.
   child-facts must include :verter/id.
   concurrent adds are safe since each child is a separate key"
  [verter
   parent-id
   child-type
   child-facts]
  (validate-child-type child-type)
  (when-not (:verter/id child-facts)
    (throw (ex-info "child-facts must include :verter/id"
                    {:child-facts child-facts})))
  (let [child-id (:verter/id child-facts)
        fam-id (family-id parent-id child-type)]
    (v/add-facts verter
                 [child-facts
                  {:verter/id fam-id
                   child-id {:state :active}}])))

(defn add-children
  "adds multiple children of a specific type to a parent in a single transaction"
  [verter
   parent-id
   child-type
   children-facts]
  (validate-child-type child-type)
  (doseq [child-facts children-facts]
    (when-not (:verter/id child-facts)
      (throw (ex-info "child facts must include :verter/id"
                      {:child-fact child-facts}))))
  (let [family-id (family-id parent-id child-type)
        facts (mapcat (fn [child-facts]
                        [child-facts 
                         {:verter/id family-id
                         (:verter/id child-facts) {:state :active}}])
                      children-facts)]
    (v/add-facts verter facts)))

(defn remove-child
  "remove child from parent's family and mark as deleted"
  ([verter
    parent-id
    child-type
    child-id]
   (remove-child verter
                 parent-id
                 child-type
                 child-id
                 nil))
  ([verter
    parent-id
    child-type
    child-id
    business-time]
   (validate-child-type child-type)
   (let [fam-id (family-id parent-id child-type)
         child-delete (if business-time
                       [{:verter/id child-id :deleted? true} business-time]
                       {:verter/id child-id :deleted? true})]
     (v/add-facts verter
                  [{:verter/id fam-id
                    child-id nil}
                   child-delete]))))

(defn find-children
  "find children of a specific type
   options:
   - :with-deleted? (default false) would/not include deleted children"
  ([verter
    parent-id
    child-type]
   (find-children verter
                  parent-id
                  child-type
                  {}))
  ([verter
    parent-id
    child-type
    {:keys [with-deleted?]
     :or {with-deleted? false}}]
   (validate-child-type child-type)
   (let [fam-id (family-id parent-id child-type)
         family-facts (v/rollup verter fam-id {:with-nils? with-deleted?})
         child-ids (->> family-facts
                       (remove (fn [[k _]] (#{:verter/id :at} k)))
                       (map first))
         children (map (fn [child-id]
                        [child-id (v/rollup verter child-id)])
                      child-ids)
         all-children (into {} children)]
     (if with-deleted?
       all-children
       (->> all-children
            (remove (fn [[_ child]] (:deleted? child)))
            (into {}))))))

(defn find-children-at
  "find children of a specific type that existed at a point in time"
  [verter
   parent-id
   child-type
   business-time]
  (validate-child-type child-type)
  (let [fam-id (family-id parent-id child-type)
        family-state (v/as-of verter fam-id business-time)
        child-ids (->> family-state
                      (remove (fn [[k _]] (#{:verter/id :at} k)))
                      (map first))
        children (map (fn [child-id]
                       [child-id (v/as-of verter child-id business-time)])
                     child-ids)]
    (into {} children)))
