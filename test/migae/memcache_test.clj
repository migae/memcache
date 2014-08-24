(ns migae.memcache-test
  (:refer-clojure :exclude (contains? get))
  (:import [com.google.appengine.tools.development.testing
            LocalServiceTestHelper
            LocalServiceTestConfig
            LocalMemcacheServiceTestConfig]
           [google.appengine.api.memcache.InvalidValueException])
  (:require [clojure.test :refer :all]
            [migae.memcache :as cache]
            [clojure.tools.logging :as log :only [trace debug info]]))

(defn- mc-fixture
  [test-fn]
  (let [helper (LocalServiceTestHelper.
                (into-array LocalServiceTestConfig
                            [(LocalMemcacheServiceTestConfig.)]))]
    (do (.setUp helper)
        (cache/get-memcache-service)
        (test-fn)
        (.tearDown helper))))

(use-fixtures :each mc-fixture)

(deftest ^:init mc-init
  (testing "MC init"
    (is (= com.google.appengine.api.memcache.MemcacheServiceImpl
           (class (cache/get-memcache-service))))
    (is (= com.google.appengine.api.memcache.MemcacheServiceImpl
           (class @cache/*memcache-service*)))))

;; api:
;; cache/statistics
;; cache/clear-all!
;; cache/contains?
;; cache/delete!
;; cache/get
;; cache/put!
;; cache/put-map!
;; cache/increment!
;; cache/increment-map!


(deftest basic-ops
  (is (not (cache/has? "one")))
  (is (nil? (cache/lookup "one")))
  (is (cache/miss "one" 1))
  (is (cache/has? "one"))
  (is (= 1 (cache/lookup "one")))
  (is (cache/miss "two" 2))
  (is (cache/delete! "one"))
  (is (not (cache/has? "one")))
  (is (cache/has? "two"))
  (is (= 2 (cache/lookup "two")))
  (is (cache/increment! "two" 1))
  (is (= 3 (cache/lookup "two")))
  (is (cache/increment! "two" 3))
  (is (= 6 (cache/lookup "two")))
  (cache/clear-all!)
  (is (not (cache/has? "two"))))

;; ################################
;;    miss
(deftest ^:miss test-miss-1
  (testing "mc miss 1"
    (is (= (cache/has? "yar")
           false))
    (cache/miss "yar" "foo")
    (is (= (cache/has? "yar")
           true))))


;; ################
;;  replacement policies
;;  options:  :always, :add-if-not-present, and :replace-only
(deftest ^:policy test-miss-policy-1
  (testing "mc miss policy 1"
    (is (= (:hit-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 0))
    (is (= (cache/has? "yar") false))
    (is (= (:hit-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 1))
    (cache/miss "yar" "foo" :always)
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (cache/has? "yar") true))
    (is (= (:hit-count (cache/statistics)) 1))
    ))



;; ################################
;;    put!

 ;; assertFalse(ms.contains("yar"));
 ;;    ms.put("yar", "foo");
 ;;    assertTrue(ms.contains("yar"));
(deftest ^:put test-put-1
  (testing "mc put 1"
    (is (= (cache/contains? "yar")
           false))
    (cache/put! "yar" "foo")
    (is (= (cache/contains? "yar")
           true))))
(deftest ^:put test-put-2
  (testing "mc put 2"
    (is (= (cache/contains? "yar") false))
    (cache/put! "yar" "foo")
    (is (= (cache/get "yar") "foo"))
    (cache/put! "yar" "bar")
    (is (= (cache/contains? "yar") true))
    (cache/put! "yar" "bar")
    (is (= (cache/get "yar") "bar"))
    ))

(deftest ^:put test-put-3
  (testing "mc put 3"
    (let [stats (cache/statistics)]
      (is (= (cache/contains? "yar") false))
      (cache/put! "yar" "foo" :policy :replace-only)
      (is (= (cache/contains? "yar") false))
      (is (= (:item-count stats)
             (:item-count (cache/statistics))))
      )))

(deftest ^:stats test-stats-1
  (testing "mc stats"
    ;; (println (cache/statistics))
    (is (= (:item-count (cache/statistics))
           0))
    (cache/put! "key1" "val1")
    (is (= (:item-count (cache/statistics)
           1)))
    ;; (cache/put! "key2" "val2")
    ;; (is (= (:item-count (cache/statistics)
    ;;        2)))
    ))

(deftest ^:stats test-stats-2
  (testing "mc stats 2"
    (is (= (:item-count (cache/statistics))
           0))
    (cache/put! "key1" "val1")
    (cache/put! "key2" "val2")
    (cache/put! "key3" "val3")
    (is (= (:item-count (cache/statistics)
           3)))
    (cache/clear-all!)
    (is (= (:item-count (cache/statistics)
           0)))
    ))
(deftest ^:stats test-stats-3
  (testing "mc stats 3"
    (is (= (cache/contains? "yar") false))
    (cache/put! "yar" "foo" :policy :replace-only)
    (is (= (cache/contains? "yar") false))
    (is (= (:item-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 2))
    ))
(deftest ^:stats test-stats-3a
  (testing "mc stats 3a"
    (is (= (cache/contains? "yar") false))
    (cache/put! "yar" "foo" :policy :replace-only)
    (is (= (cache/contains? "yar") false))
    (cache/put! "yar" "foo" :policy :always)
    (is (= (cache/contains? "yar") true))
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 2))
    ))
(deftest ^:stats test-stats-4
  (testing "mc stats 4"
    (is (= (cache/contains? "yar") false))
    (is (= (:miss-count (cache/statistics)) 1))
    (cache/put! "yar" "foo" :policy :always)
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (cache/contains? "yar") true))
    (is (= (:hit-count (cache/statistics)) 1))
    ))
(deftest ^:stats test-stats-5
  (testing "mc stats 5"
    (is (= (cache/contains? "yar") false))
    (is (= (:item-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 0))

    (cache/put! "yar" "foo" :policy :add-if-not-present)
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 0))

    (is (= (cache/get "yar") "foo"))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 1))

    (cache/put! "yar" "bar" :policy :add-if-not-present)
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 1))

    (is (= (cache/get "yar") "foo"))
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 2))

    (is (= (cache/contains? "yar") true))
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 3))
    ))

(deftest ^:incr test-incr-1
  (testing "mc incr 1"
    (is (= (cache/contains? "yar") false))
    (is (= (:item-count (cache/statistics)) 0))
    (is (= (:hit-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 1))

    (cache/increment! "yar" 1)
    (is (= (:item-count (cache/statistics)) 0))
    (is (= (:hit-count (cache/statistics)) 0))
    (is (= (:miss-count (cache/statistics)) 2))

    (cache/increment! "yar" 1 :initial 0)
    (is (= (:item-count (cache/statistics)) 1))
    (is (= (:hit-count (cache/statistics)) 1))
    (is (= (:miss-count (cache/statistics)) 2))

    (is (= (cache/get "yar") 1))
    (is (= (:hit-count (cache/statistics)) 2))

    (is (= (cache/contains? "yar") true))
    (is (= (:hit-count (cache/statistics)) 3))
    ))

(deftest ^:incr test-incr-2
  (testing "mc incr 2"
    (is (= (cache/contains? "yar") false))
    (cache/put! "yar" "foo")
    (try (cache/increment! "yar" 1)
         (catch Exception e
           (is (= "Non-incrementable value for key 'yar'"
                  (.getMessage e)))))
    ))
