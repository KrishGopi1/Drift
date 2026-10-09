package app.drift.model

/** C1. Where a displayed number came from. Every value shown in the UI carries one. */
enum class Provenance { MEASURED, DERIVED, ESTIMATED, USER_ENTERED }

enum class ConfidenceLevel { LOW, MEDIUM, HIGH }

/**
 * Uncertainty band for an ESTIMATED value, in the value's own units.
 * [coverage] is the nominal probability mass inside [lower]..[upper] (e.g. 0.9 for a 90% band).
 */
data class Confidence<T : Comparable<T>>(
    val lower: T,
    val upper: T,
    val coverage: Double,
    val level: ConfidenceLevel,
) {
    init {
        require(lower <= upper) { "Confidence lower bound must be <= upper bound" }
        require(coverage in 0.0..1.0) { "coverage must be in 0..1" }
    }
}

/**
 * C1. A value plus its provenance. [confidence] is required when [provenance] is ESTIMATED.
 * [sourceIds] lists which inputs it was derived from (empty for pure priors).
 */
data class Quantity<T : Comparable<T>>(
    val value: T,
    val provenance: Provenance,
    val confidence: Confidence<T>? = null,
    val sourceIds: Set<SourceId> = emptySet(),
) {
    init {
        require(provenance != Provenance.ESTIMATED || confidence != null) {
            "ESTIMATED quantities must carry a confidence band"
        }
    }

    fun <R : Comparable<R>> map(transform: (T) -> R): Quantity<R> = Quantity(
        value = transform(value),
        provenance = provenance,
        confidence = confidence?.let {
            val a = transform(it.lower); val b = transform(it.upper)
            Confidence(minOf(a, b), maxOf(a, b), it.coverage, it.level)
        },
        sourceIds = sourceIds,
    )
}
