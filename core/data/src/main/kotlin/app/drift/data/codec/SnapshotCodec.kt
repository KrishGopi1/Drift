package app.drift.data.codec

import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Interval
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.SourceId
import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.CatchUp
import app.drift.model.engine.Cause
import app.drift.model.engine.CauseKind
import app.drift.model.engine.Chronotype
import app.drift.model.engine.ChronotypeLabel
import app.drift.model.engine.CircadianEstimate
import app.drift.model.engine.DailyDebt
import app.drift.model.engine.DayCurve
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyCurve
import app.drift.model.engine.EnergyLabel
import app.drift.model.engine.EnergyPotential
import app.drift.model.engine.PotentialDay
import app.drift.model.engine.EnergyPhase
import app.drift.model.engine.EnergyPoint
import app.drift.model.engine.EnergySegment
import app.drift.model.engine.EngineState
import app.drift.model.engine.EngineVersion
import app.drift.model.engine.Explanation
import app.drift.model.engine.HabitCard
import app.drift.model.engine.HabitKind
import app.drift.model.engine.MetricKey
import app.drift.model.engine.NapAdvice
import app.drift.model.engine.Recommendations
import app.drift.model.engine.Reentrainment
import app.drift.model.engine.RegularityReport
import app.drift.model.engine.Sufficiency
import app.drift.model.engine.SufficiencyTier
import app.drift.model.engine.TradeOff
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Serializable
data class ConfDto<V>(val lower: V, val upper: V, val coverage: Double, val level: ConfidenceLevel)

/** Generic quantity surrogate; Duration and Instant payloads travel as ISO strings, doubles as doubles. */
@Serializable
data class QDto<V>(
    val value: V,
    val provenance: Provenance,
    val confidence: ConfDto<V>? = null,
    val sourceIds: List<String> = emptyList(),
)

@Serializable data class DailyDebtDto(val date: String, val sleep: QDto<String>?, val need: String, val contribution: String, val debtAfter: String, val imputed: Boolean)
@Serializable data class ChronotypeDto(val msfsc: QDto<Double>, val label: ChronotypeLabel, val freeNightsUsed: Int, val questionnaireWeight: Double)
@Serializable data class TrendPointDto(val date: String, val value: Double)
@Serializable
data class RegularityDto(
    val sri7: QDto<Double>?, val sri14: QDto<Double>?, val onsetSpread: QDto<String>?, val wakeSpread: QDto<String>?,
    val midpointSpread: QDto<String>?, val socialJetlag: QDto<String>?, val chronotype: ChronotypeDto?, val trend: List<TrendPointDto>,
)
@Serializable data class ReentrainmentDto(val remainingShift: String, val eastward: Boolean, val estimatedDaysLeft: Int)
@Serializable
data class CircadianDto(
    val dlmo: QDto<Double>?, val melatoninWindow: IntervalDto?, val windDownStart: String?,
    val shiftWorkMode: Boolean, val reentrainment: ReentrainmentDto?,
)
@Serializable data class PointDto(val at: String, val level: Double)
@Serializable data class SegmentDto(val phase: EnergyPhase, val interval: IntervalDto, val extremumAt: String?)
@Serializable
data class DayCurveDto(
    val date: String, val points: List<PointDto>, val segments: List<SegmentDto>, val tooFlatForDip: Boolean,
    val confidence: ConfidenceLevel, val provenance: Provenance,
)
@Serializable data class EnergyCurveDto(val days: List<DayCurveDto>, val calibrationCheckIns: Int)
@Serializable data class HabitDto(val kind: HabitKind, val window: IntervalDto, val basis: String)
@Serializable data class NapDto(val window: IntervalDto, val maxDuration: String, val reason: String)
@Serializable data class TradeOffDto(val message: String, val bedtimeIfFollowingWindow: String)
@Serializable
data class RecommendationsDto(
    val sleepOpportunity: QDto<String>, val bedtime: QDto<String>, val wakeTime: QDto<String>,
    val tradeOff: TradeOffDto?, val habits: List<HabitDto>, val nap: NapDto?,
)
@Serializable data class CauseDto(val kind: CauseKind, val date: String?, val delta: Double?, val text: String)
@Serializable data class ExplanationDto(val metric: MetricKey, val headline: String, val causes: List<CauseDto>)
@Serializable data class VersionDto(val major: Int, val minor: Int, val patch: Int)
@Serializable data class EngineStateDto(val engineVersion: VersionDto, val asOf: String, val values: Map<String, Double>)
@Serializable
data class SufficiencyDto(
    val tier: SufficiencyTier, val daysOfHistory: Int, val informativeNights: Int, val nightsUntilPersonalNeed: Int,
    val debtAvailable: Boolean, val regularityAvailable: Boolean,
)

@Serializable data class PotentialDayDto(val date: String, val score: Double, val label: EnergyLabel)
@Serializable data class PotentialDto(val score: QDto<Double>, val label: EnergyLabel, val trend: List<PotentialDayDto>)
@Serializable data class ProjectionDto(val date: String, val debt: String)
@Serializable data class CatchUpDto(val nightsToGreat: Int?, val projection: List<ProjectionDto>)
@Serializable
data class SnapshotDto(
    val engineVersion: VersionDto,
    val inputHash: String,
    val computedAt: String,
    val sleepNeed: QDto<String>,
    val sleepDebt: QDto<String>?,
    val debtBand: DebtBand?,
    val debtTrend: List<DailyDebtDto>,
    val regularity: RegularityDto,
    val circadian: CircadianDto,
    val energyCurve: EnergyCurveDto,
    val recommendations: RecommendationsDto,
    val explanations: List<ExplanationDto>,
    val state: EngineStateDto,
    val dataSufficiency: SufficiencyDto,
    val energyPotential: PotentialDto? = null,
    val catchUp: CatchUpDto? = null,
)

// ---- generic quantity mapping ------------------------------------------------------------------

private fun <T : Comparable<T>, V> Quantity<T>.dto(enc: (T) -> V) = QDto(
    enc(value), provenance,
    confidence?.let { ConfDto(enc(it.lower), enc(it.upper), it.coverage, it.level) },
    sourceIds.map { it.value }.sorted(),
)

private fun <T : Comparable<T>, V> QDto<V>.model(dec: (V) -> T) = Quantity(
    dec(value), provenance,
    confidence?.let { Confidence(dec(it.lower), dec(it.upper), it.coverage, it.level) },
    sourceIds.mapTo(LinkedHashSet()) { SourceId(it) },
)

private fun Quantity<Duration>.durDto() = dto { it.toString() }
private fun QDto<String>.dur() = model { Duration.parse(it) }
private fun Quantity<Double>.dblDto() = dto { it }
private fun QDto<Double>.dbl() = model { it }
private fun Quantity<Instant>.instDto() = dto { it.toString() }
private fun QDto<String>.inst() = model { Instant.parse(it) }

private fun EngineVersion.dto() = VersionDto(major, minor, patch)
private fun VersionDto.model() = EngineVersion(major, minor, patch)

fun EngineState.toDto() = EngineStateDto(engineVersion.dto(), asOf.toString(), values)
fun EngineStateDto.toModel() = EngineState(engineVersion.model(), Instant.parse(asOf), values)

// ---- snapshot -----------------------------------------------------------------------------------

fun AnalysisSnapshot.toDto() = SnapshotDto(
    engineVersion = engineVersion.dto(),
    inputHash = inputHash,
    computedAt = computedAt.toString(),
    sleepNeed = sleepNeed.durDto(),
    sleepDebt = sleepDebt?.durDto(),
    debtBand = debtBand,
    debtTrend = debtTrend.map {
        DailyDebtDto(it.date.toString(), it.sleep?.durDto(), it.need.toString(), it.contribution.toString(), it.debtAfter.toString(), it.imputed)
    },
    regularity = RegularityDto(
        regularity.sri7?.dblDto(), regularity.sri14?.dblDto(), regularity.onsetSpread?.durDto(), regularity.wakeSpread?.durDto(),
        regularity.midpointSpread?.durDto(), regularity.socialJetlag?.durDto(),
        regularity.chronotype?.let { ChronotypeDto(it.msfscMinutes.dblDto(), it.label, it.freeNightsUsed, it.questionnaireWeight) },
        regularity.trend.map { TrendPointDto(it.first.toString(), it.second) },
    ),
    circadian = CircadianDto(
        circadian.dlmoMinutes?.dblDto(), circadian.melatoninWindow?.toDto(), circadian.windDownStart?.toString(),
        circadian.shiftWorkMode,
        circadian.reentrainment?.let { ReentrainmentDto(it.remainingShift.toString(), it.eastward, it.estimatedDaysLeft) },
    ),
    energyCurve = EnergyCurveDto(
        energyCurve.days.map { d ->
            DayCurveDto(
                d.date.toString(), d.points.map { PointDto(it.at.toString(), it.level) },
                d.segments.map { SegmentDto(it.phase, it.interval.toDto(), it.extremumAt?.toString()) },
                d.tooFlatForDip, d.confidence, d.provenance,
            )
        },
        energyCurve.calibrationCheckIns,
    ),
    recommendations = RecommendationsDto(
        recommendations.sleepOpportunity.durDto(), recommendations.bedtime.instDto(), recommendations.wakeTime.instDto(),
        recommendations.tradeOff?.let { TradeOffDto(it.message, it.bedtimeIfFollowingWindow.toString()) },
        recommendations.habits.map { HabitDto(it.kind, it.window.toDto(), it.basis) },
        recommendations.nap?.let { NapDto(it.window.toDto(), it.maxDuration.toString(), it.reason) },
    ),
    explanations = explanations.map { e ->
        ExplanationDto(e.metric, e.headline, e.causes.map { CauseDto(it.kind, it.date?.toString(), it.delta, it.text) })
    },
    state = state.toDto(),
    dataSufficiency = SufficiencyDto(
        dataSufficiency.tier, dataSufficiency.daysOfHistory, dataSufficiency.informativeNights,
        dataSufficiency.nightsUntilPersonalNeed, dataSufficiency.debtAvailable, dataSufficiency.regularityAvailable,
    ),
    energyPotential = energyPotential?.let { p ->
        PotentialDto(p.score.dblDto(), p.label, p.trend.map { PotentialDayDto(it.date.toString(), it.score, it.label) })
    },
    catchUp = catchUp?.let { c -> CatchUpDto(c.nightsToGreat, c.projection.map { ProjectionDto(it.first.toString(), it.second.toString()) }) },
)

fun SnapshotDto.toModel() = AnalysisSnapshot(
    engineVersion = engineVersion.model(),
    inputHash = inputHash,
    computedAt = Instant.parse(computedAt),
    sleepNeed = sleepNeed.dur(),
    sleepDebt = sleepDebt?.dur(),
    debtBand = debtBand,
    debtTrend = debtTrend.map {
        DailyDebt(LocalDate.parse(it.date), it.sleep?.dur(), Duration.parse(it.need), Duration.parse(it.contribution), Duration.parse(it.debtAfter), it.imputed)
    },
    regularity = RegularityReport(
        regularity.sri7?.dbl(), regularity.sri14?.dbl(), regularity.onsetSpread?.dur(), regularity.wakeSpread?.dur(),
        regularity.midpointSpread?.dur(), regularity.socialJetlag?.dur(),
        regularity.chronotype?.let { Chronotype(it.msfsc.dbl(), it.label, it.freeNightsUsed, it.questionnaireWeight) },
        regularity.trend.map { LocalDate.parse(it.date) to it.value },
    ),
    circadian = CircadianEstimate(
        circadian.dlmo?.dbl(), circadian.melatoninWindow?.toModel(), circadian.windDownStart?.let(Instant::parse),
        circadian.shiftWorkMode,
        circadian.reentrainment?.let { Reentrainment(Duration.parse(it.remainingShift), it.eastward, it.estimatedDaysLeft) },
    ),
    energyCurve = EnergyCurve(
        energyCurve.days.map { d ->
            DayCurve(
                LocalDate.parse(d.date), d.points.map { EnergyPoint(Instant.parse(it.at), it.level) },
                d.segments.map { EnergySegment(it.phase, it.interval.toModel(), it.extremumAt?.let(Instant::parse)) },
                d.tooFlatForDip, d.confidence, d.provenance,
            )
        },
        energyCurve.calibrationCheckIns,
    ),
    recommendations = Recommendations(
        recommendations.sleepOpportunity.dur(), recommendations.bedtime.inst(), recommendations.wakeTime.inst(),
        recommendations.tradeOff?.let { TradeOff(it.message, Instant.parse(it.bedtimeIfFollowingWindow)) },
        recommendations.habits.map { HabitCard(it.kind, it.window.toModel(), it.basis) },
        recommendations.nap?.let { NapAdvice(it.window.toModel(), Duration.parse(it.maxDuration), it.reason) },
    ),
    explanations = explanations.map { e ->
        Explanation(e.metric, e.headline, e.causes.map { Cause(it.kind, it.date?.let(LocalDate::parse), it.delta, it.text) })
    },
    state = state.toModel(),
    dataSufficiency = Sufficiency(
        dataSufficiency.tier, dataSufficiency.daysOfHistory, dataSufficiency.informativeNights,
        dataSufficiency.nightsUntilPersonalNeed, dataSufficiency.debtAvailable, dataSufficiency.regularityAvailable,
    ),
    energyPotential = energyPotential?.let { p ->
        EnergyPotential(p.score.dbl(), p.label, p.trend.map { PotentialDay(LocalDate.parse(it.date), it.score, it.label) })
    },
    catchUp = catchUp?.let { c -> CatchUp(c.nightsToGreat, c.projection.map { LocalDate.parse(it.date) to Duration.parse(it.debt) }) },
)

/** JSON codec shared by storage and backup. Non-finite doubles are allowed because engines may emit them. */
val DriftJson: Json = Json {
    allowSpecialFloatingPointValues = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object SnapshotCodec {
    fun encode(snapshot: AnalysisSnapshot): String = DriftJson.encodeToString(SnapshotDto.serializer(), snapshot.toDto())
    fun decode(json: String): AnalysisSnapshot = DriftJson.decodeFromString(SnapshotDto.serializer(), json).toModel()
}
