(ns stallmarketops.store
  "SSoT for the ISIC-4789 'Retail sale via stalls and markets of other
  goods' (the catch-all market-stall vertical for general/other goods not
  covered by ISIC 4781's food/beverage/tobacco stalls or ISIC 4782's
  textiles/clothing/footwear stalls -- household goods, hardware,
  electronics accessories, books, toys, and similar) operations-
  COORDINATION actor, behind a `Store` protocol so the backend is a swap,
  not a rewrite -- the same seam every `cloud-itonami-isic-*` actor in
  this fleet uses.

  This actor coordinates the back-office operations of a market-stall
  vendor: sales/inventory transaction logging, stall
  placement/staffing scheduling, inventory supply-order coordination, and
  compliance-concern flagging (permit legitimacy, suspected counterfeit
  goods, product-quality/safety observations). It never directly issues
  or finalizes a market-stall permit, and never directly resolves a
  vendor/customer dispute -- see `stallmarketops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `stalls` directory keyed by `:stall-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that has plagued earlier sibling actors).

  A registered/verified stall record (market/council stall-permit
  registration) must exist before ANY proposal targeting that stall may
  ever commit or escalate -- `stallmarketops.governor`'s
  `stall-unverified-violations` re-derives this from the stall's own
  `:registered?`/`:verified?` fields, never from proposal self-report.
  This single check gates every op in the closed allowlist, including
  `:coordinate-supply-order` -- this vertical, being the catch-all
  'other goods' class, deliberately does NOT add a second supply-chain
  vendor-verification entity the way ISIC 4719's sibling actor does; the
  stall's own permit-verification is the sole gate for this R0.

  The ledger stays append-only: which stall a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (stall-record [s stall-id] "Registered market-stall record, or nil.
    Stall map: {:stall-id .. :name .. :registered? bool :verified? bool}.")
  (all-stall-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-stall-records [s stalls] "replace/seed the stall directory (map stall-id->stall)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained stall directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:stalls
   {"stall-1" {:stall-id "stall-1" :name "Riverside Market Stall 12 (Household Goods)"
               :registered? true :verified? true}
    "stall-2" {:stall-id "stall-2" :name "Harborside Market Stall 7 (Hardware & Tools)"
               :registered? true :verified? true}
    "stall-3" {:stall-id "stall-3" :name "Pop-Up Weekend Market Pitch 3 (permit in review)"
               :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (stall-record [_ stall-id] (get-in @a [:stalls stall-id]))
  (all-stall-records [_] (sort-by :stall-id (vals (:stalls @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-stall-records [s stalls] (when (seq stalls) (swap! a assoc :stalls stalls)) s))

(defn seed-db
  "A MemStore seeded with the demo stall directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `stalls` map (stall-id string ->
  record map) -- the primary test/dev entry point. May be empty (an
  unregistered-everywhere market)."
  ([] (mem-store {}))
  ([stalls]
   (->MemStore (atom {:stalls (or stalls {}) :ledger [] :coordination-log []}))))
