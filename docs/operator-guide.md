# Operator Guide

## First Deployment
1. Register the market operator and stalls; independently confirm each
   stall's market/council permit registration before seeding
   `stallmarketops.store`.
2. Import existing sales/inventory, staffing and supply-order history.
3. Run read-only sales-record-logging and stall-operation dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-supply-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run compliance-concern flag and audit export.

## Minimum Production Controls
- stall registration/permit-verification check before ANY proposal for
  that stall, including supply orders
- governor gate on every proposal before commit
- human sign-off for `:flag-compliance-concern` (always) and high-cost
  `:coordinate-supply-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process

## Certification
Certified operators must prove stall-verification discipline,
governor-bypass resistance, evidence-backed compliance-concern reporting
and human review for every escalation-gated action.
