package com.jongrady.traincue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingModelsTest {
    @Test
    fun nestedRunCuesAreParsedAndKeptVisible() {
        val days = parseDays(
            """
            [
              {
                "id": "week1-thursday",
                "title": "Week 1 Thursday",
                "subtitle": "Easy Run",
                "items": [
                  {
                    "id": "w1-thursday-run",
                    "type": "run",
                    "label": "Easy Run",
                    "distanceKm": 2.4,
                    "workouts": [
                      { "name": "2.4 km run", "sets": 1, "reps": "2.4 km", "distanceKm": 2.4 },
                      { "name": "5 min warm-up walk", "sets": 1, "reps": "5 min" },
                      { "name": "Conversational run/walk", "sets": 1, "reps": "complete" },
                      { "name": "5 min cool-down walk", "sets": 1, "reps": "5 min" }
                    ]
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        val run = days.single().items.single()
        assertEquals(4, run.workouts.size)
        assertEquals(2.4, run.workouts.first().distanceKm ?: 0.0, 0.001)
        assertEquals(1, days.single().sessionSteps().size)
        assertTrue(days.single().sessionSteps().single() is SessionStep.Run)
    }

    @Test
    fun exerciseStepsTrackSetsAndDayCompletion() {
        val block = PlanItem(
            id = "strength",
            type = "strength",
            label = "Strength",
            workouts = listOf(
                WorkoutItem(id = "squat", name = "Squat", sets = 3, reps = "10"),
                WorkoutItem(id = "press", name = "Press", sets = 2, reps = "8"),
            ),
        )
        val day = TrainingDay("day", "Week 1 Monday", "Strength", listOf(block))
        val keys = day.sessionSteps().map { it.key }.toSet()

        assertEquals(2, day.sessionSteps().size)
        assertTrue(day.isComplete(keys, emptySet()))
        assertTrue(day.isComplete(emptySet(), setOf("day")))
    }
}
