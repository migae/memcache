(ns org.mobileink.migae.memcache
  (:refer-clojure :exclude (contains? get))
  (:import [com.google.appengine.api.memcache
            Expiration
            MemcacheService
            MemcacheServiceFactory
            MemcacheService$SetPolicy]))

(defonce ^{:dynamic true} *memcache-service* (atom nil))
(defonce ^{:dynamic true} *namespaced-memcache-services* (atom {}))

(def policies {:always MemcacheService$SetPolicy/SET_ALWAYS
               :add-if-not-present MemcacheService$SetPolicy/ADD_ONLY_IF_NOT_PRESENT
               :replace-only MemcacheService$SetPolicy/REPLACE_ONLY_IF_PRESENT})


(defn memcache [& {:keys [namespace]}]
  (if (nil? namespace)
      (do (when (nil? @*memcache-service*)
            (reset! *memcache-service* (MemcacheServiceFactory/getMemcacheService)))
          @*memcache-service*)
      (let [s (@*namespaced-memcache-services* namespace)]
        (if-not (nil? s)
            s
            ((swap! *namespaced-memcache-services* assoc
                    namespace (MemcacheServiceFactory/getMemcacheService namespace))
             namespace)))))

(defn get-memcache-service [& {:keys [namespace]}]
  (if (nil? namespace)
      (do (when (nil? @*memcache-service*)
            (reset! *memcache-service* (MemcacheServiceFactory/getMemcacheService)))
          @*memcache-service*)
      (let [s (@*namespaced-memcache-services* namespace)]
        (if-not (nil? s)
            s
            ((swap! *namespaced-memcache-services* assoc
                    namespace (MemcacheServiceFactory/getMemcacheService namespace))
             namespace)))))


(defrecord Statistics [bytes-returned-for-hits
                       hit-count  ;; successful .get + .contains
                       miss-count ;; unsuccessful .get + .contains
                       item-count
                       max-time-without-access
                       total-item-bytes])


(defn statistics [& {:keys [namespace]}]
  (let [stats (.getStatistics (get-memcache-service :namespace namespace))]
    (Statistics. (.getBytesReturnedForHits stats)
                 (.getHitCount stats) ;; successful .get + .contains
                 (.getMissCount stats) ;; unsuccessful .get + .contains
                 (.getItemCount stats)
                 (.getMaxTimeWithoutAccess stats)
                 (.getTotalItemBytes stats))))


(defn clear-all!
  "Clears the entire cache. Does not respect namespaces!"
  []
  (.clearAll (get-memcache-service)))

;; mimic clojure.core.cache
(defn has? [key & {:keys [namespace]}]
  (.contains (get-memcache-service :namespace namespace) key))

;; deprecate
(defn contains? [key & {:keys [namespace]}]
  (.contains (get-memcache-service :namespace namespace) key))

;; mimic clojure.core.cache
;; (defn evict!
;;   "If (sequential? key-or-keys), deletes in batch."
;;   [key-or-keys & {:keys [namespace millis-no-readd]}]
;;   (let [service (get-memcache-service :namespace namespace)]
;;     (if millis-no-readd
;;         (if (sequential? key-or-keys)
;;             (.deleteAll service key-or-keys millis-no-readd)
;;             (.delete service key-or-keys millis-no-readd))
;;         (if (sequential? key-or-keys)
;;             (.deleteAll service key-or-keys)
;;             (.delete service key-or-keys)))))

;; deprecate
(defn delete!
  "If (sequential? key-or-keys), deletes in batch."
  [key-or-keys & {:keys [namespace millis-no-readd]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if millis-no-readd
        (if (sequential? key-or-keys)
            (.deleteAll service key-or-keys millis-no-readd)
            (.delete service key-or-keys millis-no-readd))
        (if (sequential? key-or-keys)
            (.deleteAll service key-or-keys)
            (.delete service key-or-keys)))))


(defn- to-entity-cast [value] value)
  ;; (if (and (= :interactive (gae/gae-environment-type))
  ;;          (instance? EntityProtocol value))
  ;;     (let [obj-meta (merge (meta value) {:type (.getName (class value))})
  ;;           obj-map (into {} value)]
  ;;       (with-meta obj-map obj-meta))
  ;;     value))


(defn- to-entity-cast-many [value-map] value-map)
  ;; (if (= :interactive (gae/gae-environment-type))
  ;;     (into {} (map (fn [[k v]] [k (to-entity-cast v)]) value-map))
  ;;     value-map))


;; (defn- from-entity-cast [value] value)
;;   (if (and (= :interactive (gae/gae-environment-type))
;;            (not (nil? (meta value)))
;;            (clojure.core/contains? (meta value) :type))
;;       (let [claimed-class (Class/forName (:type (meta value)))]
;;         (with-meta (u/record claimed-class value) (dissoc (meta value) :type)))
;;       value))


(defn- from-entity-cast-many [value-map]
  ;; (if (= :interactive (gae/gae-environment-type))
  ;;     (into {} (map (fn [[k v]] [k (from-entity-cast v)]) value-map))
      (into {} value-map))



;; mimic clojure.core.cache
(defn hit
  []
 "Is meant to be called if the cache is determined to contain a value
   associated with `e`")


;; mimic clojure.core.cache
  ;; (lookup [cache e]
  ;;         [cache e not-found]
  ;; core.cache docstring:
  ;; "Retrieve the value associated with `e` if it exists, else `nil` in
  ;;  the 2-arg case.  Retrieve the value associated with `e` if it exists,
  ;;  else `not-found` in the 3-arg case."
(defn lookup
  "If (sequential? key-or-keys), returns values as a map."
  [key-or-keys & {:keys [namespace]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if (sequential? key-or-keys)
      (into {} (.getAll service key-or-keys))
      (.get service key-or-keys))))

;; deprecate
(defn get
  "If (sequential? key-or-keys), returns values as a map."
  [key-or-keys & {:keys [namespace]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if (sequential? key-or-keys)
        (into {} (.getAll service key-or-keys))
        (.get service key-or-keys))))


(defn miss
  ([key value] (.put (get-memcache-service) key value))

  ;; third arg may be either expiration or policy
  ([key value third] (if (= (class third) Expiration); com.google.appengine.api.memcache)
                       (.put (memcache) key value third)
                       (.put (memcache) key value nil (policies third))))

  ([key value expiration policy] (.put (memcache) key value expiration policy)))

;; deprecate
(defn put! [& args] (miss args))


;; deprecate
(defn put-map! [values & {:keys [namespace expiration policy]
                          :or {policy :always}}]
  (let [service (get-memcache-service :namespace namespace)
        policy (policies policy)]
    (.putAll service (to-entity-cast-many values) expiration policy)))


(defn increment!
  "If (sequential? key-or-keys), increment each key by the delta."
  [key-or-keys delta & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (if (sequential? key-or-keys)
            (.incrementAll service key-or-keys delta (long initial))
            (.increment service key-or-keys delta (long initial)))
        (if (sequential? key-or-keys)
            (.incrementAll service key-or-keys delta)
            (.increment service key-or-keys delta)))))


(defn increment-map! [values & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (.incrementAll service values (long initial))
        (.incrementAll service values))))
