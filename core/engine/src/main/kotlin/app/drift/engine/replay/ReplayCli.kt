package app.drift.engine.replay

import app.drift.engine.DefaultSleepEngine
import app.drift.engine.ENGINE_VERSION
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

private val json = Json { ignoreUnknownKeys = true }

/** Entry point: `./gradlew :core:engine:run --args="<export.json> [--now-hour=12]"`. */
fun main(args: Array<String>) {
    val path = args.firstOrNull { !it.startsWith("--") }
    if (path == null) {
        System.err.println("usage: ReplayCli <export.json> [--now-hour=12]")
        exitProcess(2)
    }
    val nowHour = args.firstOrNull { it.startsWith("--now-hour=") }?.substringAfter('=')?.toIntOrNull() ?: 12
    val file = File(path)
    if (!file.isFile) {
        System.err.println("not a file: $path")
        exitProcess(2)
    }
    val data = ReplayConverter.convert(json.decodeFromString(ReplayFile.serializer(), file.readText()))
    val rows = Replay.run(data, DefaultSleepEngine(), nowHour)
    println("Drift engine $ENGINE_VERSION replay of ${file.name} (${data.episodes.size} episodes, zone ${data.zone})")
    println(Replay.format(rows))
}
