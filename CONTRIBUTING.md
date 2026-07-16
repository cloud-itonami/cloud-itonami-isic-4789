# Contributing

`cloud-itonami-isic-4789` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real vendor, customer, market-operator or
  compliance-incident data.
- Keep sales-record logging, stall-operation scheduling, supply-order
  coordination and compliance-concern flagging behind the
  StallMarketGovernor.
- Treat market-stall-operations workflows as high-risk: add tests for
  stall permit-verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "permit", "dispute") -- phrase it as the finalization/execution ACTION
  (e.g. "issued the permit", "resolved the dispute"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-compliance-concern` happy path -- see
  `stallmarketops.governor/scope-excluded-terms`'s docstring.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
