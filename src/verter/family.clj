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
   child-facts must include :verter/id"
  [verter
   parent-id
   child-type
   child-facts]
  (validate-child-type child-type)
  (when-not (:verter/id child-facts)
    (throw (ex-info "child-facts must include :verter/id"
                    {:child-facts child-facts})))
  (let [child-id (:verter/id child-facts)
        fam-id (family-id parent-id child-type)
        current-ids (get (v/rollup verter fam-id) :ids #{})]
    (v/add-facts verter
                 [child-facts
                  {:verter/id fam-id
                   :ids (conj current-ids child-id)}])))

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
         current-ids (get (v/rollup verter fam-id) :ids #{})
         child-delete (if business-time
                       [{:verter/id child-id :deleted? true} business-time]
                       {:verter/id child-id :deleted? true})]
     (v/add-facts verter
                  [{:verter/id fam-id
                    :ids (disj current-ids child-id)}
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
         all-ids (->> (v/facts verter fam-id)
                      (keep :ids)
                      (reduce into #{}))
         children (map (fn [child-id]
                        [child-id (v/rollup verter child-id)])
                      all-ids)
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
        child-ids (get family-state :ids #{})
        children (map (fn [child-id]
                       [child-id (v/as-of verter child-id business-time)])
                     child-ids)]
    (into {} children)))
