# cloud-itonami-isic-4789

Open Business Blueprint for **ISIC Rev.5 4789**: retail sale via stalls
and markets of other goods -- the catch-all market-stall vertical for
general/other goods not covered by sibling ISIC 4781 (food, beverages
and tobacco stalls) or ISIC 4782 (textiles, clothing and footwear
stalls): household goods, hardware, electronics accessories, books,
toys, and similar merchandise sold from a market stall or pitch.

This repository publishes a market-stall-vendor
operations-COORDINATION actor -- sales/inventory transaction logging,
stall placement/staffing scheduling, inventory supply-order
coordination, and compliance-concern flagging -- as an OSS business that
any qualified operator can fork, deploy, run, improve and sell, so an
independent market-stall vendor never surrenders its operations data to
a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **StallMarketAdvisor
⊣ StallMarketGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:stall-market-governor`, is a
distinct, independent build (no naming-collision precedent question --
distinct from ISIC 4719's own `:merchandise-retail-governor`).

> **Why an actor layer at all?** An LLM is great at drafting a sales-
> record summary, a stall-placement proposal, or a supply-order request
> -- but it has no license to actually issue a market-stall permit, no
> way to independently confirm a stall is actually registered and
> permit-verified, no authority to resolve a vendor/customer dispute,
> and no notion of when a "flag this concern" op quietly turns into a
> claim to have already acted on it. Letting it act directly invites an
> unverified stall's data entering the ledger, or -- worst of all -- a
> fabricated claim to have issued a permit or resolved a dispute,
> exposing the market operator and its vendors to real liability. This
> project seals the StallMarketAdvisor into a single node and wraps it
> with an independent **StallMarketGovernor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: coordination only, not permit issuance or dispute resolution

This actor is **operations coordination only**. It never performs or
authorizes:

- directly issuing or finalizing a market-stall permit
- directly resolving a vendor/customer dispute

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a compliance
concern for a human to triage is exactly this actor's job --
`:flag-compliance-concern` is never excluded by this check, only
directly issuing a permit or directly resolving a dispute is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`stallmarketops.governor`'s `effect-not-propose-violations` HARD check
and `stallmarketops.phase`'s phase table, which never puts
`:flag-compliance-concern` in any phase's `:auto` set). A human
market-stall coordinator is always the one who actually acts on a
flagged concern or confirms a high-cost supply order.

## The core contract

```
stall/market-permit registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ StallMarket-          │ ─────────────▶ │ StallMarketGovernor          │  (independent system)
   │ Advisor (sealed)      │  + citations    │ stall-unverified ·           │
   └───────────────────────┘                 │ effect-not-propose ·         │
          │                 commit ◀┼         │ scope-excluded (permit-      │
          │                         │         │ issuance/dispute-resolution  │
    record + ledger        escalate ┼         │ finalization) ·              │
          │              (ALWAYS for│         │ op-not-allowed               │
          │       :flag-compliance- │         └────────────────────────────┘
          │       concern/high-cost │
          │       supply-order)     │
          ▼
      human approval
```

**The StallMarketAdvisor never commits a proposal the StallMarketGovernor
would reject, and a compliance-concern flag or a high-cost supply order
never commits without a human sign-off.** Hard violations (an
unregistered/permit-unverified stall; a non-`:propose` effect; content
touching permit-issuance/dispute-resolution finalization; an op outside
the closed allowlist) force **hold** and *cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: unloading, display setup,
point-of-sale handling) under human/robot floor operations gated by
market policy. This actor itself does not dispatch robot/hardware
actions -- it is strictly the operations-coordination layer
(sales-record logging, stall-placement scheduling, supply-order
coordination, compliance-concern flagging) any physical-dispatch layer
could eventually feed proposals into, always gated the same way by the
independent StallMarketGovernor.

## Features

- **Closed proposal-op allowlist**: `log-sales-record`,
  `schedule-stall-operation`, `coordinate-supply-order`,
  `flag-compliance-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Stall unverified** -- the target stall's market/council permit
     registration must exist AND be independently registered/verified
     in the store. This single check gates EVERY op in the closed
     allowlist, including `:coordinate-supply-order` -- this vertical,
     being the catch-all "other goods" class, deliberately does not add
     a second supply-chain vendor-verification entity the way ISIC
     4719's sibling actor does.
  2. **Effect is :propose** -- any other `:effect` value is rejected.
  3. **Scope exclusion** -- directly issuing/finalizing a market-stall
     permit, or directly resolving a vendor/customer dispute, is a
     permanent block.
  4. **Op not allowed** -- an op outside the closed four-op allowlist.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-compliance-concern` -- ALWAYS escalates, regardless of
    confidence or phase. A "flag a concern" op is never auto-commit
    eligible and never finalizes a permit or dispute decision itself --
    it only surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: sales-record logging only (approval-gated)
  - Phase 2: + stall-operation scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (compliance concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/stallmarketops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/stallmarketops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/stallmarketops/phase_test.clj` -- rollout phase logic
- `test/stallmarketops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/stallmarketops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `stallmarketops.store` -- SSoT (MemStore, String-keyed stall
  directory, append-only ledger)
- `stallmarketops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `stallmarketops.governor` -- independent compliance layer
- `stallmarketops.phase` -- staged rollout (0→3)
- `stallmarketops.operation` -- langgraph-clj StateGraph
- `stallmarketops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4789`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Sales/inventory transaction logging (`:log-sales-record`) | Real POS/inventory-system integration |
| Stall placement/staffing scheduling coordination (`:schedule-stall-operation`) | Direct staff time-clock/payroll integration |
| Inventory supply-order coordination, HARD-gated on the target stall's own permit verification (`:coordinate-supply-order`) | Real supplier-ordering-system integration; a dedicated supply-chain vendor-verification entity (unlike ISIC 4719's sibling actor) |
| Compliance-concern flagging (permit legitimacy, counterfeit goods, product quality/safety), ALWAYS human-gated (`:flag-compliance-concern`) | Directly issuing/finalizing any market-stall permit, or directly resolving any vendor/customer dispute -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Daily reconciliation/cash-up -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a stall-transfer
or a cash-discrepancy-escalation check) as its own governed op with its
own HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `StallMarketAdvisor` + `StallMarketGovernor` run as
real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor.

## License

Code and implementation templates are AGPL-3.0-or-later.
