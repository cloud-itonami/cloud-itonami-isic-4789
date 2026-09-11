(ns stallmarketops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean sales-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a stall-operation-scheduling request and a low-cost
  supply-order coordination (both auto-commit clean at phase 3), then a
  high-cost supply-order (ALWAYS escalates regardless of phase), then a
  compliance-concern flag (ALWAYS escalates, at any phase -- approve,
  then commit), then HARD-hold scenarios: an unregistered stall, a stall
  registered but not yet permit-verified, a proposal whose own `:effect`
  is not `:propose`, and a proposal that has drifted into the
  permanently-excluded permit-issuance/dispute-resolution-finalization
  scope."
  (:require [langgraph.graph :as g]
            [stallmarketops.advisor :as advisor]
            [stallmarketops.store :as store]
            [stallmarketops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "market-stall-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :market-stall-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :market-stall-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-sales-record stall-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-sales-record :stall-id "stall-1"
                                  :patch {:units-sold 18 :stock-count-delta -18}} coordinator-phase-1)]
      (println r)
      (println "-- human market-stall coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-sales-record stall-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-sales-record :stall-id "stall-1"
                                  :patch {:units-sold 12 :stock-count-delta -12}} coordinator-phase-3))

    (println "\n== schedule-stall-operation stall-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-stall-operation :stall-id "stall-1"
                                  :patch {:pitch "market-north-row-3" :date "2026-07-20" :window "08:00-16:00"}} coordinator-phase-3))

    (println "\n== coordinate-supply-order stall-1, low cost (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-supply-order :stall-id "stall-1"
                                  :patch {:item "household-goods restock" :quantity 60 :estimated-cost 350.0}} coordinator-phase-3))

    (println "\n== coordinate-supply-order stall-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-supply-order :stall-id "stall-1"
                                 :patch {:item "seasonal display fixtures" :quantity 10 :estimated-cost 2400.0}} coordinator-phase-3)]
      (println r)
      (println "-- human market-stall coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-compliance-concern stall-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-compliance-concern :stall-id "stall-1"
                                 :patch {:concern "suspected counterfeit electronics accessories on display, permit renewal date unclear" :confidence 0.9}} coordinator-phase-3)]
      (println r)
      (println "-- human market-stall coordinator reviews & approves --")
      (println (approve! actor "t6")))

    (println "\n== log-sales-record stall-99 (unregistered stall -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-sales-record :stall-id "stall-99"
                                  :patch {:units-sold 0}} coordinator-phase-3))

    (println "\n== log-sales-record stall-3 (registered but permit unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-sales-record :stall-id "stall-3"
                                  :patch {:units-sold 5}} coordinator-phase-3))

    (println "\n== schedule-stall-operation stall-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t9" {:op :schedule-stall-operation :stall-id "stall-1"
                                           :patch {:pitch "market-south-row-1" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-sales-record stall-1, advisor drifts into permit-issuance/dispute-resolution scope -> HARD hold, permanent ==")
    (println (exec-op actor "t10" {:op :log-sales-record :stall-id "stall-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
