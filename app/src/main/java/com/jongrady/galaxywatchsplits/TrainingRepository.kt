package com.jongrady.traincue

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

internal const val ROUTINE_FEED_URL = "https://raw.githubusercontent.com/MisunderstoodJedi/TrainCue/main/routines.json"

internal class TrainingRepository(context: Context) {
    private val prefs = context.getSharedPreferences("train_plan", Context.MODE_PRIVATE)

    fun loadDays(): List<TrainingDay> = runCatching {
        prefs.getString("items", null)?.let(::parseDays).orEmpty()
    }.getOrDefault(emptyList())

    fun hasSavedPlan(): Boolean = prefs.contains("items")

    fun saveDays(days: List<TrainingDay>) {
        prefs.edit().putString("items", days.toJsonArray().toString()).apply()
    }

    fun loadCompletedSteps(): Set<String> = prefs.getStringSet("completed", emptySet()).orEmpty().toSet()

    fun saveCompletedSteps(completed: Set<String>) {
        prefs.edit().putStringSet("completed", completed.toSet()).apply()
    }

    fun loadCompletedDays(): Set<String> {
        val legacyDeleted = prefs.getStringSet("deleted_day_ids", emptySet()).orEmpty()
        val completedDays = prefs.getStringSet("completed_day_ids", emptySet()).orEmpty()
        return (legacyDeleted + completedDays).toSet()
    }

    fun saveCompletedDays(completedDays: Set<String>) {
        prefs.edit().putStringSet("completed_day_ids", completedDays.toSet()).apply()
    }

    fun loadActiveSession(): ActiveSession? = runCatching {
        prefs.getString("active_session", null)?.let { JSONObject(it).toActiveSession() }
    }.getOrNull()

    fun saveActiveSession(session: ActiveSession?) {
        val edit = prefs.edit()
        if (session == null) edit.remove("active_session") else edit.putString("active_session", session.toJson().toString())
        edit.apply()
    }

    fun loadHistory(): List<WorkoutLog> = runCatching {
        val array = JSONArray(prefs.getString("workout_history", "[]"))
        List(array.length()) { array.getJSONObject(it).toWorkoutLog() }
    }.getOrDefault(emptyList())

    fun addHistory(log: WorkoutLog) {
        val history = (listOf(log) + loadHistory().filterNot { it.id == log.id }).take(60)
        val array = JSONArray().also { target -> history.forEach { target.put(it.toJson()) } }
        prefs.edit().putString("workout_history", array.toString()).apply()
    }
}

internal class RemotePlanImporter(private val feedUrl: String = ROUTINE_FEED_URL) {
    suspend fun load(): List<TrainingDay> = withContext(Dispatchers.IO) {
        val raw = try {
            val connection = URL(feedUrl.withCacheBuster()).openConnection() as HttpURLConnection
            connection.run {
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
                requestMethod = "GET"
                val status = responseCode
                if (status !in 200..299) throw PlanSyncException("GitHub HTTP $status")
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (error: SocketTimeoutException) {
            throw PlanSyncException("GitHub timeout", error)
        } catch (error: UnknownHostException) {
            throw PlanSyncException("No internet", error)
        } catch (error: IOException) {
            throw PlanSyncException("Network error", error)
        }
        if (raw.isBlank()) throw PlanSyncException("GitHub file empty")
        try {
            parseDays(raw)
        } catch (error: JSONException) {
            throw PlanSyncException("Invalid plan JSON", error)
        }
    }

    private fun String.withCacheBuster(): String {
        val separator = if (contains("?")) "&" else "?"
        return "$this${separator}updated=${System.currentTimeMillis()}"
    }
}

internal class PlanSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun Throwable.syncMessage(): String = when (this) {
    is PlanSyncException -> message ?: "Sync failed"
    is JSONException -> "Invalid plan"
    is SocketTimeoutException -> "GitHub timeout"
    is UnknownHostException -> "No internet"
    is IOException -> "Network error"
    else -> "Sync failed"
}

internal fun parseDays(raw: String): List<TrainingDay> {
    val trimmed = raw.trim()
    val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("routines")
    return List(array.length()) { array.getJSONObject(it).toTrainingDay() }
}

private fun JSONObject.toTrainingDay(): TrainingDay {
    val array = getJSONArray("items")
    return TrainingDay(
        id = getString("id"),
        title = getString("title"),
        subtitle = optString("subtitle", ""),
        items = List(array.length()) { array.getJSONObject(it).toPlanItem() },
    )
}

private fun JSONObject.toPlanItem(): PlanItem {
    val workoutsArray = optJSONArray("workouts")
    return PlanItem(
        id = getString("id"),
        type = getString("type"),
        label = getString("label"),
        distanceKm = distanceKmOrNull(),
        workouts = workoutsArray?.let { array -> List(array.length()) { array.getJSONObject(it).toWorkoutItem() } }.orEmpty(),
    )
}

private fun JSONObject.toWorkoutItem(): WorkoutItem = WorkoutItem(
    id = optString("id").takeIf { it.isNotBlank() },
    name = getString("name"),
    sets = optInt("sets", 1).coerceAtLeast(1),
    reps = opt("reps")?.toString().orEmpty().ifBlank { "complete" },
    note = optString("note", ""),
    imageAsset = optString("imageAsset").takeIf { it.isNotBlank() },
    distanceKm = distanceKmOrNull(),
)

private fun JSONObject.distanceKmOrNull(): Double? = when {
    has("distanceKm") -> optDouble("distanceKm").takeIf { !it.isNaN() }
    has("distanceMiles") -> optDouble("distanceMiles").takeIf { !it.isNaN() }?.times(MILES_TO_KM)
    else -> null
}

private fun List<TrainingDay>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

private fun TrainingDay.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("subtitle", subtitle)
    .put("items", JSONArray().also { array -> items.forEach { array.put(it.toJson()) } })

private fun PlanItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("type", type)
    .put("label", label)
    .also { json ->
        distanceKm?.let { json.put("distanceKm", it) }
        if (workouts.isNotEmpty()) json.put("workouts", JSONArray().also { array -> workouts.forEach { array.put(it.toJson()) } })
    }

private fun WorkoutItem.toJson(): JSONObject = JSONObject()
    .also { json -> id?.let { json.put("id", it) } }
    .put("name", name)
    .put("sets", sets)
    .put("reps", reps)
    .put("note", note)
    .also { json ->
        imageAsset?.let { json.put("imageAsset", it) }
        distanceKm?.let { json.put("distanceKm", it) }
    }

private fun ActiveSession.toJson(): JSONObject = JSONObject()
    .put("dayId", dayId)
    .put("startedAt", startedAt)
    .put("stepIndex", stepIndex)
    .put("completedSets", JSONObject(completedSets))
    .put("runMode", runMode?.name)
    .put("runDistanceKm", runDistanceKm)
    .put("runSeconds", runSeconds)

private fun JSONObject.toActiveSession(): ActiveSession {
    val setsObject = optJSONObject("completedSets") ?: JSONObject()
    val completedSets = setsObject.keys().asSequence().associateWith { setsObject.optInt(it) }
    return ActiveSession(
        dayId = getString("dayId"),
        startedAt = getLong("startedAt"),
        stepIndex = optInt("stepIndex", 0),
        completedSets = completedSets,
        runMode = optString("runMode").takeIf { it.isNotBlank() }?.let { runCatching { RunMode.valueOf(it) }.getOrNull() },
        runDistanceKm = optDouble("runDistanceKm", 0.0),
        runSeconds = optLong("runSeconds", 0),
    )
}

private fun WorkoutLog.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("dayId", dayId)
    .put("title", title)
    .put("subtitle", subtitle)
    .put("completedAt", completedAt)
    .put("durationSeconds", durationSeconds)
    .put("completedSteps", completedSteps)
    .put("totalSteps", totalSteps)
    .put("effort", effort)
    .put("runMode", runMode?.name)
    .put("distanceKm", distanceKm)

private fun JSONObject.toWorkoutLog(): WorkoutLog = WorkoutLog(
    id = getString("id"),
    dayId = getString("dayId"),
    title = getString("title"),
    subtitle = optString("subtitle", ""),
    completedAt = getLong("completedAt"),
    durationSeconds = getLong("durationSeconds"),
    completedSteps = getInt("completedSteps"),
    totalSteps = getInt("totalSteps"),
    effort = optInt("effort", 3),
    runMode = optString("runMode").takeIf { it.isNotBlank() }?.let { runCatching { RunMode.valueOf(it) }.getOrNull() },
    distanceKm = optDouble("distanceKm", 0.0),
)
