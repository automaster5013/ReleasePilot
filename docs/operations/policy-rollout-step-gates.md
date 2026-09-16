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

## Live AWS validation (2026-09-16)

An isolated `releasepilot-e2e` release was requested by `jupyter5013@gmail.com` (DEVELOPER) and approved by the distinct `kaiser5013@gmail.com` (APPROVER). Release `c3e7b97c-2d71-4d22-8f4e-a138355bae35` paused at native step 1 with the stable service returning v2 and the canary service returning v1. Step 0 observed for 60 seconds and completed all five rules with PASS before promotion. Native step 3 then paused for a separate 60-second step 1 analysis; all five rules passed before the release became SUCCEEDED. Both persisted policy steps ended PASSED and the native Rollout ended Healthy on the requested digest.

The run exposed a transition race: immediately after the image patch, observation could copy the prior revision's completed native step index into policy progress. Commit `839de77` makes native observation update only the observed resource version; policy progress now advances only after a guarded promotion succeeds. Targeted regression tests, CI run 35049863342, Docker Hub CD run 35049863500, GitOps sync, and the resumed two-stage live release all passed. The bounded load Job was deleted afterward.

This validation used one replica and direct stable/canary service traffic. It proves independent analysis gates and service targeting, not an exact 20% HTTP traffic split. Exact request-weight validation still requires an Argo-supported traffic router.
