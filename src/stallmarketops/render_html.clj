(ns stallmarketops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave2 flagship item2): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`stallmarketops.operation` -> `stallmarketops.governor` ->
  `stallmarketops.store`) through a scenario adapted from this repo's
  own `stallmarketops.sim` demo driver (`clojure -M:dev:run`, confirmed
  BEFORE writing this file to exercise the real seeded stall directory
  ids `stall-1`..`stall-3` -- they match `stallmarketops.store/demo-data`,
  and the `:commit` node genuinely calls `store/commit-record!` so the
  coordination log grows by one real entry per committed op), trimmed to
  a representative subset (phase-3 auto-commit lifecycle, ALWAYS-escalate
  + human approval, and distinct HARD-hold reasons that never reach a
  human) and rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [stallmarketops.store :as store]
            [stallmarketops.advisor :as advisor]
            [stallmarketops.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness --------------------------------

(def ^:private operator
  {:actor-id "coord-1" :actor-role :market-stall-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "market-stall-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: stall-1 clears three write ops that auto-commit
  clean at phase 3 (log-sales-record, schedule-stall-operation, a
  low-cost coordinate-supply-order); stall-1's high-cost
  coordinate-supply-order ALWAYS escalates (estimated-cost 2400.0
  exceeds `governor/supply-cost-threshold` 800.0) and is approved by a
  human; stall-1's flag-compliance-concern ALWAYS escalates (per
  `governor/always-escalate-ops`) even though clean, and is approved;
  stall-99 (absent from the seed) HARD-holds on `:stall-unverified`;
  stall-3 (registered but NOT `:verified?` in the seed) HARD-holds on
  `:stall-unverified`; an advisor that claims direct actuation
  (`:effect :commit`) HARD-holds on `:effect-not-propose`; a proposal
  with `:out-of-scope? true` HARD-holds on `:scope-excluded`. Every HARD
  hold never reaches a human. Returns the resulting store -- every field
  read by `render` below is real governor/store output, not a hand-typed
  copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "s1-log" {:op :log-sales-record :stall-id "stall-1"
                            :patch {:units-sold 18 :stock-count-delta -18}})

    (exec! actor "s1-schedule" {:op :schedule-stall-operation :stall-id "stall-1"
                                 :patch {:pitch "market-north-row-3"
                                         :date "2026-07-20"
                                         :window "08:00-16:00"}})

    (exec! actor "s1-supply-low" {:op :coordinate-supply-order :stall-id "stall-1"
                                   :patch {:item "household-goods restock"
                                           :quantity 60 :estimated-cost 350.0}})

    (exec! actor "s1-supply-high" {:op :coordinate-supply-order :stall-id "stall-1"
                                    :patch {:item "seasonal display fixtures"
                                            :quantity 10 :estimated-cost 2400.0}})
    (approve! actor "s1-supply-high")

    (exec! actor "s1-flag" {:op :flag-compliance-concern :stall-id "stall-1"
                             :patch {:concern "suspected counterfeit electronics accessories on display, permit renewal date unclear"
                                     :confidence 0.9}})
    (approve! actor "s1-flag")

    ;; HARD: unregistered stall
    (exec! actor "s99-log" {:op :log-sales-record :stall-id "stall-99"
                             :patch {:units-sold 0}})

    ;; HARD: stall-3 registered but permit unverified
    (exec! actor "s3-log" {:op :log-sales-record :stall-id "stall-3"
                            :patch {:units-sold 5}})

    ;; HARD: effect not :propose
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                 (-advise [_ _ req]
                                                   (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "s1-direct" {:op :schedule-stall-operation :stall-id "stall-1"
                                        :patch {:pitch "market-south-row-1"
                                                :date "2026-07-22"}}))

    ;; HARD: scope-excluded (permit-issuance / dispute-resolution finalization)
    (exec! actor "s1-scope" {:op :log-sales-record :stall-id "stall-1"
                              :out-of-scope? true :patch {}})
    db))

;; ----------------------------- rendering ------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger stall-id]
  (last (filter #(= (:stall-id %) stall-id) ledger)))

(defn- status-cell [ledger stall-id]
  (let [f (last-fact-for ledger stall-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (-> f :basis first))]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- stall-row [ledger {:keys [stall-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc stall-id) (esc name)
          (cond
            (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
            registered? "<span class=\"warn\">registered, unverified</span>"
            :else "<span class=\"critical\">unregistered</span>")
          (status-cell ledger stall-id)))

(defn- ledger-row [{:keys [t op stall-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc stall-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) (str %))) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- coord-row [{:keys [op stall-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc stall-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; Ops, stallmarketops.governor / .phase) -- documentation of fixed
  ;; behavior, not runtime telemetry.
  ["        <tr><td><code>:log-sales-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span> &middot; HARD hold if stall unverified</td></tr>"
   "        <tr><td><code>:schedule-stall-operation</code></td><td><span class=\"ok\">phase-3 auto when clean</span> &middot; HARD hold if stall unverified or effect not :propose</td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"ok\">phase-3 auto when clean &amp; cost &le; 800</span> &middot; ALWAYS human approval above estimated-cost 800 &middot; HARD hold if stall unverified</td></tr>"
   "        <tr><td><code>:flag-compliance-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span> &middot; observation only; finalizing permit issuance or dispute resolution is permanently out of scope</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        stalls (store/all-stall-records db)
        coords (store/coordination-log db)
        stall-rows (str/join "\n" (map (partial stall-row ledger) stalls))
        coord-rows (str/join "\n" (map coord-row coords))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4789 &middot; market-stall other-goods ops</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Retail sale via stalls and markets of other goods (ISIC 4789) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never issues stall permits or resolves disputes</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Market stalls (permit registration directory)</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>stallmarketops.store</code> via <code>stallmarketops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Stall</th><th>Name</th><th>Permit status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     stall-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log</h2>\n"
     "    <p class=\"muted\">Proposals that actually committed to the SSoT (auto-commit or human-approved). HARD holds never appear here.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Stall</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     coord-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (StallMarket Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Stall registration/permit-verification is independently re-derived from the store; <code>:effect</code> must be <code>:propose</code>; finalizing a market-stall permit or resolving a vendor/customer dispute is permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Stall</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
