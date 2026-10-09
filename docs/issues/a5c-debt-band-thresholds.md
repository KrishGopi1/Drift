# a5c: expose debt band thresholds and free-night minimum in the snapshot

Problem: the Sleep screen shows the basis of the debt bands (plan 4.3: Low under 5 h, High over 10 h, 14-night window) and
the Body clock screen needs the free-night minimum (plan 4.4: 4). `AnalysisSnapshot` does not carry them, so
`feature/sleep` holds copies (`DebtBandBasis`, `FREE_NIGHTS_NEEDED`, `MIN_NIGHTS`). They can drift from the engine's ParameterSet.

Proposed change: add `bands: DebtBandThresholds(low: Duration, high: Duration, windowNights: Int)` and
`minFreeNights: Int` to `AnalysisSnapshot` (or `Sufficiency`). Blocks: nothing; the copies work until then.
