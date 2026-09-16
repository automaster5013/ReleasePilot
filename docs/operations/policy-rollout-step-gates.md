# Policy and Rollout step gates

ReleasePilot requires each CANARY policy weight to correspond to one native Argo `setWeight` followed by an indefinite `pause`. For a 20/100 policy:

```yaml
strategy:
  canary:
    stableService: sample-checkout-stable
    canaryService: sample-checkout-canary
    steps:
      - setWeight: 20
      - pause: {}
      - setWeight: 100
      - pause: {}
```

The start connector compares policy weights and native steps before patching the image. Missing, extra, mismatched, or timed steps are rejected with `ROLLOUT_POLICY_STEPS_MISMATCH`; it does not rewrite an operator's Rollout configuration. BLUE_GREEN requires one 100 policy step and `autoPromotionEnabled: false`.

Promotion requires a COMPLETED PASS analysis for the current policy step. The HTTP operation rejects premature manual promotion with `ROLLOUT_ANALYSIS_PASS_REQUIRED`; the command handler checks again before any Kubernetes control patch, including automatic actions. A promotion command carries its expected policy step index; retries for an already advanced native step do not clear the next pause. A native policy pause is required and a separate operator pause blocks promotion. Healthy native state alone cannot mark the release successful: all policy steps must have passed.

For percentage traffic routing, configure an Argo-supported traffic router. Without one, setWeight controls replica proportions and direct stable/canary service traffic is independently generated. The isolated sample uses direct service requests to verify both analysis tracks; these requests do not prove a 20% HTTP traffic split.

External modifications after release start can still cause native controller state to diverge. ReleasePilot refuses successful completion when required policy steps remain incomplete; operators should investigate and abort such a release rather than force-promote it outside ReleasePilot.
