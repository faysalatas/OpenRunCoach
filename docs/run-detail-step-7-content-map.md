# Step 7: old tab content inventory

Approved by the owner before implementation. Every old tab's sourced data and actions stay available; Fastest 30 seconds is explicitly omitted at the owner's request.

| Old location / content | New location |
|---|---|
| Overview: name, date, distance, duration, pace | Gradient hero, subtitle and metric trio |
| Overview: average heart rate | Heart-rate card (including recorded-summary fallback) |
| Route | 170 dp route card; existing MapLibre and Canvas fallback |
| Pace chart, average, target | Pace card; elapsed-time spacing, segments/gaps and scale retained; target band only for eligible whole-run targets |
| Heart-rate chart, average/max | Heart-rate card, only with data |
| Cadence chart, moving/session average and maximum | Cadence card with expandable chart, only with data; cadence mini tile when available |
| Summary/route/metric source labels | Data sources disclosure |
| Recording clipping diagnostic, source/duplicate details, canonical identity, device, matching reasons | Nested Recording diagnostics, Source and duplicate/source details |
| Metric values: distance, HR, speed, cadence, power, VO₂max, elevation; provenance, confidence and verification | Existing Run Metrics / Metric details inside Recording diagnostics |
| Refresh metrics and metrics update/error messages | Retained diagnostics action and visible status/error text |
| Inspect Health Data | Health data inspector link |
| Ignore/restore run | Visible secondary button at the bottom; confirmation/cancel before existing visibility handler |
| Splits: kilometre/partial distance and pace | Proportional SplitBars; exact table retained in Split details |
| Split delta, HR, derivation | Exact Split details table and derivation text |
| Whole-run average and half comparison | Split summary |
| Half distances, durations, paces and interpretation threshold | Half-run details disclosure |
| Split distance, duration, cadence | Existing nested Split details disclosure |
| Analysis: completion, session cost, volume | Evidence beneath Plan vs. run |
| Planned/actual distance or duration, RPE, whole-run pace | Three-column Plan vs. run table |
| Work-segment target versus actual whole-run average | Distinct Work pace / Avg. pace rows and original caution |
| Continuity, confidence, adherence, pace response and full reasons | Analysis facts / Evidence below Plan vs. run |
| Association, candidates, change, unassign and cancel | Planned workout association section with unchanged handlers |
| Coach action, observations, next implication | Short coach block plus full Coach Insight details |
| Full response/decision reasons, confidence and feedback summary | More details disclosure |
| Checkout fields, edit/cancel, save/update and status/errors | Existing editor retained in Step 7; moved to dedicated screen in Step 8 |

Metric trio kept per spec and Run2.dc.html source; PNG omitted it.

The referenced HTML files are absent from this checkout. The owner's explicit resolution supplies the intended 44/46 title, date/time/source subtitle and 34/38 metric trio. No other reference conflict was found.
