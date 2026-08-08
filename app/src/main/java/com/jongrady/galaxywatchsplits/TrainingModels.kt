package com.jongrady.traincue

internal const val MILES_TO_KM = 1.609344

internal data class TrainingDay(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<PlanItem>,
)

internal data class PlanItem(
    val id: String,
    val type: String,
    val label: String,
    val distanceKm: Double? = null,
    val workouts: List<WorkoutItem> = emptyList(),
)

internal data class WorkoutItem(
    val id: String? = null,
    val name: String,
    val sets: Int,
    val reps: String,
    val note: String = "",
    val imageAsset: String? = null,
    val distanceKm: Double? = null,
)

internal sealed interface SessionStep {
    val key: String
    val block: PlanItem

    data class Run(override val block: PlanItem) : SessionStep {
        override val key: String = block.completionKey()
    }

    data class Exercise(
        override val block: PlanItem,
        val workout: WorkoutItem,
    ) : SessionStep {
        override val key: String = block.workoutCompletionKey(workout)
    }

    data class Simple(override val block: PlanItem) : SessionStep {
        override val key: String = block.completionKey()
    }
}

internal enum class RunMode(val label: String) {
    OUTDOOR("Outdoor"),
    TREADMILL("Treadmill"),
    MANUAL("No tracking"),
}

internal data class ActiveSession(
    val dayId: String,
    val startedAt: Long,
    val stepIndex: Int = 0,
    val completedSets: Map<String, Int> = emptyMap(),
    val runMode: RunMode? = null,
    val runDistanceKm: Double = 0.0,
    val runSeconds: Long = 0,
)

internal data class WorkoutLog(
    val id: String,
    val dayId: String,
    val title: String,
    val subtitle: String,
    val completedAt: Long,
    val durationSeconds: Long,
    val completedSteps: Int,
    val totalSteps: Int,
    val effort: Int,
    val runMode: RunMode? = null,
    val distanceKm: Double = 0.0,
)

internal fun TrainingDay.sessionSteps(): List<SessionStep> = buildList {
    items.forEach { block ->
        when {
            block.type.equals("run", ignoreCase = true) -> add(SessionStep.Run(block))
            block.workouts.isNotEmpty() -> block.workouts.forEach { add(SessionStep.Exercise(block, it)) }
            else -> add(SessionStep.Simple(block))
        }
    }
}

internal fun TrainingDay.weekLabel(): String = title.substringBeforeLast(' ', title)

internal fun TrainingDay.shortDayLabel(): String = title.substringAfterLast(' ', title)

internal fun TrainingDay.isComplete(completed: Set<String>, completedDays: Set<String>): Boolean {
    if (id in completedDays) return true
    val steps = sessionSteps()
    return steps.isNotEmpty() && steps.all { it.key in completed }
}

internal fun PlanItem.completionKey(): String = id

internal fun PlanItem.workoutCompletionKey(workout: WorkoutItem): String {
    return workout.id?.takeIf { it.isNotBlank() }?.let { "$id:$it" } ?: legacyWorkoutCompletionKey(workout)
}

internal fun PlanItem.workoutCompletionKeys(workout: WorkoutItem): List<String> {
    return listOf(workoutCompletionKey(workout), legacyWorkoutCompletionKey(workout)).distinct()
}

internal fun Set<String>.isWorkoutCompleted(item: PlanItem, workout: WorkoutItem): Boolean {
    return item.workoutCompletionKeys(workout).any { it in this }
}

private fun PlanItem.legacyWorkoutCompletionKey(workout: WorkoutItem): String {
    return "$id:${workout.name.trim().uppercase()}"
}
