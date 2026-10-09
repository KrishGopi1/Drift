package app.drift.testing.fakes

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/** One recorded call on a fake: the method name and its arguments in declaration order. */
data class RecordedCall(val name: String, val args: List<Any?>)

/**
 * Shared behaviour of every fake: records each call (so UI tests can assert "button X caused effect Y")
 * and injects scripted failures by method name.
 *
 * ```
 * repo.failNext("saveProfile", IOException("disk full"))
 * // ... tap Save ...
 * assertThat(repo.callsTo("saveProfile")).hasSize(1)
 * ```
 */
open class Recording {
    private val log = CopyOnWriteArrayList<RecordedCall>()
    private val faults = ConcurrentHashMap<String, ArrayDeque<Throwable>>()
    private val permanent = ConcurrentHashMap<String, Throwable>()

    /** Every call so far, oldest first. */
    val calls: List<RecordedCall> get() = log.toList()

    fun callsTo(name: String): List<RecordedCall> = log.filter { it.name == name }
    fun wasCalled(name: String): Boolean = log.any { it.name == name }
    fun callCount(name: String): Int = log.count { it.name == name }
    fun clearCalls() = log.clear()

    /** The next [times] calls to [name] throw [error] (after being recorded). */
    fun failNext(name: String, error: Throwable, times: Int = 1) {
        val q = faults.getOrPut(name) { ArrayDeque() }
        synchronized(q) { repeat(times) { q.addLast(error) } }
    }

    /** Every call to [name] throws [error] until [clearFaults]. */
    fun failAlways(name: String, error: Throwable) { permanent[name] = error }

    fun clearFaults() { faults.clear(); permanent.clear() }

    /** Records the call, then throws if a fault is scripted for it. Fakes call this first in every method. */
    protected fun record(name: String, vararg args: Any?) {
        log.add(RecordedCall(name, args.toList()))
        faultCheck(name)
    }

    protected fun faultCheck(name: String) {
        permanent[name]?.let { throw it }
        val q = faults[name] ?: return
        val e = synchronized(q) { q.removeFirstOrNull() }
        if (e != null) throw e
    }
}
