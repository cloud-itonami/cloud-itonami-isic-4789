(ns stallmarketops.store-contract-test
  "Contract tests for `stallmarketops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [stallmarketops.store :as store]))

(deftest mem-store-stall-lookup
  (testing "MemStore can store and retrieve stalls by ID (string keys)"
    (let [stalls {"s1" {:stall-id "s1" :name "Alice's Market Stall" :registered? true :verified? true}}
          s (store/mem-store stalls)]
      (is (some? (store/stall-record s "s1")))
      (is (nil? (store/stall-record s "s99"))))))

(deftest mem-store-all-stall-records
  (testing "MemStore returns all stalls in sorted order"
    (let [stalls {"s2" {:stall-id "s2" :name "Bob's Variety Stall"}
                  "s1" {:stall-id "s1" :name "Alice's Market Stall"}
                  "s3" {:stall-id "s3" :name "Carol's Hardware Pitch"}}
          s (store/mem-store stalls)
          all-s (store/all-stall-records s)]
      (is (= 3 (count all-s)))
      (is (= "s1" (:stall-id (first all-s))))
      (is (= "s3" (:stall-id (last all-s)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-sales-record :stall-id "s1" :value {:units-sold 18}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-stall-records
  (testing "MemStore with-stall-records replaces the stall directory"
    (let [s (store/mem-store {})
          new-stalls {"s1" {:stall-id "s1" :name "Alice's Market Stall"}}]
      (is (= 0 (count (store/all-stall-records s))))
      (store/with-stall-records s new-stalls)
      (is (= 1 (count (store/all-stall-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo stalls"
    (let [s (store/seed-db)]
      (is (> (count (store/all-stall-records s)) 0))
      (is (some? (store/stall-record s "stall-1")))
      (is (some? (store/stall-record s "stall-2")))
      (is (some? (store/stall-record s "stall-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for stall-id"
    (let [demo (store/demo-data)
          stalls (:stalls demo)]
      (doseq [[k v] stalls]
        (is (string? k) "stall keys must be strings")
        (is (string? (:stall-id v)) "stall-id must be string")
        (is (= k (:stall-id v)) "key must match stall-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
