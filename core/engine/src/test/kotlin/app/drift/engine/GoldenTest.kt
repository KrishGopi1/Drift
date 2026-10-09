package app.drift.engine

import app.drift.model.engine.AnalysisSnapshot
import app.drift.model.engine.EnergyPhase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.time.ZoneId
import java.util.Properties
import java.util.TreeMap

/**
 * Pinned outputs for fixed scenarios, per engine version (plan 9.1). Any output change needs a version bump,
 * a CHANGELOG.md line and a regenerated file: copy build/golden-actual/<version>.properties to
 * src/test/resources/golden/<version>.properties after reviewing the diff.
 */
class GoldenTest {
    private fun fmt(d: Double?): String = d?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "none"

    private fun summary(s: Scenario, snap: AnalysisSnapshot): Map<String, String> {
        val z: ZoneId = s.zone
        val m = TreeMap<String, String>()
        fun clock(i: java.time.Instant?): String = i?.atZone(z)?.toLocalTime()?.toString() ?: "none"
        m["need.min"] = fmt(snap.sleepNeed.value.toMillis() / 60000.0)
        m["need.lower"] = fmt(snap.sleepNeed.confidence!!.lower.toMillis() / 60000.0)
        m["need.upper"] = fmt(snap.sleepNeed.confidence!!.upper.toMillis() / 60000.0)
        m["debt.min"] = fmt(snap.sleepDebt?.value?.toMillis()?.div(60000.0))
        m["debt.band"] = snap.debtBand?.name ?: "none"
        m["debt.imputedNights"] = snap.debtTrend.count { it.imputed }.toString()
        m["sri7"] = fmt(snap.regularity.sri7?.value)
        m["sri14"] = fmt(snap.regularity.sri14?.value)
        m["spread.onset.min"] = fmt(snap.regularity.onsetSpread?.value?.toMillis()?.div(60000.0))
        m["social.jetlag.min"] = fmt(snap.regularity.socialJetlag?.value?.toMillis()?.div(60000.0))
        m["chrono.msfsc"] = fmt(snap.regularity.chronotype?.msfscMinutes?.value)
        m["chrono.label"] = snap.regularity.chronotype?.label?.name ?: "none"
        m["dlmo"] = fmt(snap.circadian.dlmoMinutes?.value)
        m["melatonin.start"] = clock(snap.circadian.melatoninWindow?.start)
        m["winddown"] = clock(snap.circadian.windDownStart)
        m["shift"] = snap.circadian.shiftWorkMode.toString()
        m["reentrain.days"] = snap.circadian.reentrainment?.estimatedDaysLeft?.toString() ?: "none"
        m["rec.bed"] = clock(snap.recommendations.bedtime.value)
        m["rec.wake"] = clock(snap.recommendations.wakeTime.value)
        m["rec.opportunity.min"] = fmt(snap.recommendations.sleepOpportunity.value.toMillis() / 60000.0)
        m["rec.tradeoff"] = (snap.recommendations.tradeOff != null).toString()
        m["rec.nap"] = (snap.recommendations.nap != null).toString()
        val today = snap.energyCurve.days[1]
        for (h in listOf(8, 10, 14, 18, 21)) {
            val pt = today.points.minByOrNull { Math.abs(java.time.Duration.between(it.at, today.date.atTime(h, 0).atZone(z).toInstant()).toMinutes()) }!!
            m["energy.${h}h"] = fmt(pt.level)
        }
        m["energy.flat"] = today.tooFlatForDip.toString()
        m["energy.phases"] = today.segments.filter { it.phase != EnergyPhase.SLEEP }.joinToString(">") { it.phase.name }
        m["energy.dip"] = today.segments.firstOrNull { it.phase == EnergyPhase.AFTERNOON_DIP }?.extremumAt?.let { clock(it) } ?: "none"
        m["tier"] = snap.dataSufficiency.tier.name
        m["informative"] = snap.dataSufficiency.informativeNights.toString()
        return m
    }

    @Test fun `scenario outputs match the golden file for this engine version`() {
        val engine = Kit.engine
        val actual = TreeMap<String, String>()
        for (s in Synth.all()) summary(s, engine.analyze(s.input())).forEach { (k, v) -> actual["${s.name}.$k"] = v }

        val out = File("build/golden-actual").apply { mkdirs() }.resolve("$ENGINE_VERSION.properties")
        out.writeText(actual.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })

        val res = javaClass.getResourceAsStream("/golden/$ENGINE_VERSION.properties")
        assertThat(res).isNotNull() // missing: review build/golden-actual and copy it to src/test/resources/golden
        val expected = Properties().apply { res!!.use { load(it) } }
        assertThat(actual.keys).isEqualTo(expected.stringPropertyNames().toSortedSet())
        val diffs = actual.filter { (k, v) -> !same(expected.getProperty(k), v) }.map { (k, v) -> "$k expected ${expected.getProperty(k)} but was $v" }
        assertThat(diffs).isEmpty()
    }

    private fun same(expected: String, actual: String): Boolean {
        val a = expected.toDoubleOrNull()
        val b = actual.toDoubleOrNull()
        return if (a != null && b != null) Math.abs(a - b) <= 0.02 else expected == actual
    }

    @Test fun `the golden scenarios cover the plan list`() {
        val names = Synth.all().map { it.name }
        assertThat(names).containsAtLeast("steady", "chronic-short", "weekend-catch-up", "fragmented-parent", "night-shift", "missing-week")
        assertThat(names.count { it.startsWith("travel-") }).isEqualTo(2)
        assertThat(names.count { it.startsWith("dst-") }).isEqualTo(2)
    }
}
