# Governance

`cloud-itonami-isic-4789` is an OSS open-business blueprint for
market-stall operations coordination (ISIC Rev.5 4789 -- retail sale via
stalls and markets of other goods).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unregistered/permit-unverified stall can never
  commit -- for ANY op, including `:coordinate-supply-order`.
- the StallMarketGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, permit-issuance/
  dispute-resolution-finalization content, an op outside the closed
  allowlist) cannot be overridden by human approval.
- every sales-record log, stall-operation schedule, supply-order
  coordination and compliance-concern flag is auditable.
- vendor, customer and market-operator data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing sale-record, stall-scheduling, supply-order or
  compliance-concern policy checks
- mishandling vendor, customer or market-operator data
- misrepresenting certification status
- failing to respond to security or compliance incidents
