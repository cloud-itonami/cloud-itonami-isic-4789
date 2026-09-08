(ns stallmarketops.governor
  "StallMarketGovernor -- the independent compliance layer that earns the
  StallMarketAdvisor the right to commit. The advisor has no notion of
  whether a market stall is actually registered and permit-verified,
  whether its own proposed `:effect` secretly claims a direct actuation
  instead of a mere proposal, or whether it has silently drifted into a
  permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (sales/inventory transaction logging, stall placement/staffing
  scheduling, inventory supply-order coordination, compliance-concern
  flagging). It NEVER performs or authorizes:
    - directly issuing or finalizing a market-stall permit
    - directly resolving a vendor/customer dispute

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Stall unverified            -- the target stall's market/council
                                       permit registration must exist AND
                                       be independently confirmed
                                       `:registered?`/`:verified?` in the
                                       store before ANY proposal for it
                                       may commit or even escalate. Never
                                       trusts a proposal's own claim about
                                       the stall -- re-derived from the
                                       stall's own record, the same
                                       'ground truth, not self-report'
                                       discipline every sibling actor's
                                       governor uses. This single check
                                       gates EVERY op in the closed
                                       allowlist, including
                                       `:coordinate-supply-order` -- this
                                       vertical, being the catch-all
                                       'other goods' class, deliberately
                                       does not add a second supply-chain
                                       vendor-verification entity the way
                                       ISIC 4719's sibling actor does.
    2. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    3. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly issuing/finalizing a
                                       market-stall permit, or directly
                                       resolving a vendor/customer
                                       dispute, is a HARD, PERMANENT
                                       block -- this actor's charter
                                       excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-compliance-concern`
                                       itself is never excluded by this
                                       check -- surfacing a permit-
                                       legitimacy/counterfeit-goods/
                                       product-quality concern for a
                                       human is exactly this actor's job;
                                       only FINALIZING a permit or
                                       directly resolving a dispute is
                                       excluded (see `scope-excluded-terms`
                                       below -- phrased as the
                                       finalization/execution ACTION,
                                       never a bare noun like 'permit' or
                                       'dispute', so the default mock
                                       advisor's own
                                       `:flag-compliance-concern`
                                       rationale never self-trips this
                                       check).
    4. Op not allowed              -- an op outside the closed four-op
                                       allowlist (folded into check 3's
                                       implementation, listed separately
                                       here because it is a structurally
                                       distinct failure mode from content
                                       scanning).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-compliance-concern` -- ALWAYS escalates to a
      human, regardless of confidence, regardless of how clean the
      proposal otherwise is. `stallmarketops.phase` independently agrees:
      `:flag-compliance-concern` is never a member of any phase's
      `:auto` set either -- two layers, not one.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      inventory procurement proposal always needs a human sign-off, even
      when the governor and phase would otherwise allow auto-commit."
  (:require [kotoba.lang.text :as str]
            [stallmarketops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-stall general-merchandise procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  800.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`)."
  #{:log-sales-record :schedule-stall-operation
    :coordinate-supply-order :flag-compliance-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-compliance-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly issuing/finalizing
  a market-stall permit, or directly resolving a vendor/customer dispute,
  rather than merely flagging a concern for a human. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'issued the permit', 'resolved the dispute'), never a bare
  noun like 'permit', 'license', 'dispute' or 'complaint' -- a bare noun
  would accidentally match inside this actor's own legitimate
  `:flag-compliance-concern` default proposal text (whose whole job is
  to talk about permit legitimacy / counterfeit-goods / product-quality
  concerns) and self-block the happy path. See
  `stallmarketops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize permit issuance" "finalized permit issuance" "finalizing permit issuance"
   "issue the permit" "issued the permit" "issuing the permit"
   "issue the stall permit" "issued the stall permit" "issuing the stall permit"
   "issue a market permit" "issued a market permit" "issuing a market permit"
   "grant the permit" "granted the permit" "granting the permit"
   "approve the permit application" "approved the permit application" "approving the permit application"
   "finalize the stall permit" "finalized the stall permit" "finalizing the stall permit"
   "resolve the dispute" "resolved the dispute" "resolving the dispute"
   "adjudicate the dispute" "adjudicated the dispute" "adjudicating the dispute"
   "settle the dispute" "settled the dispute" "settling the dispute"
   "rule on the dispute" "ruled on the dispute" "ruling on the dispute"
   "finalize the dispute resolution" "finalized the dispute resolution" "finalizing the dispute resolution"
   "resolve the vendor dispute" "resolved the vendor dispute" "resolving the vendor dispute"
   "resolve the customer complaint" "resolved the customer complaint" "resolving the customer complaint"
   "許可証を発行した" "許可証の発行を確定" "出店許可を発行した" "出店許可の発行を確定"
   "許可証を交付した" "出店許可を交付した" "許可を付与した" "出店許可を付与した"
   "紛争を解決した" "紛争解決を確定" "苦情を裁定した" "苦情を解決した" "紛争の裁定を下した"])

;; ----------------------------- checks -----------------------------

(defn- stall-unverified-violations
  "The target stall must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:stall-id` claim without a store lookup. Gates EVERY op in the
  closed allowlist."
  [{:keys [stall-id]} st]
  (let [s (store/stall-record st stall-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :stall-unverified
        :detail (str stall-id " は未登録または未検証(許可未確認)の出店 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly issuing/finalizing a market-stall
  permit or directly resolving a vendor/customer dispute, regardless of
  confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "許可証の発行確定・紛争解決確定など、出店許可発給/紛争解決確定行為(permit-issuance/dispute-resolution finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors a StallMarketAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [stall-id (or (:stall-id proposal) (:stall-id request))
        hard (into []
                   (concat (stall-unverified-violations {:stall-id stall-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :stall-id   (:stall-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
