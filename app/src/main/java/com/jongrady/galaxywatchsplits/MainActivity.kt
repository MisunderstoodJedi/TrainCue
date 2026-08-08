package com.jongrady.traincue

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import kotlinx.coroutines.delay
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var isAmbient by mutableStateOf(false)
    private var ambientUpdateToken by mutableIntStateOf(0)
    private var burnInProtectionRequired by mutableStateOf(false)
    private var resumeRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(
            AmbientLifecycleObserver(
                this,
                object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                        burnInProtectionRequired = ambientDetails.burnInProtectionRequired
                        isAmbient = true
                    }

                    override fun onUpdateAmbient() {
                        ambientUpdateToken++
                    }

                    override fun onExitAmbient() {
                        isAmbient = false
                    }
                },
            ),
        )
        handleResumeIntent(intent)
        setContent {
            TrainCueApp(
                isAmbient = isAmbient,
                ambientUpdateToken = ambientUpdateToken,
                burnInProtectionRequired = burnInProtectionRequired,
                resumeRequest = resumeRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResumeIntent(intent)
    }

    private fun handleResumeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_RESUME_SESSION, false) == true) resumeRequest++
    }
}

private enum class AppScreen {
    Splash,
    Home,
    Plan,
    Day,
    Session,
    RunMode,
    RunTracker,
    ExerciseDetail,
    Finish,
    History,
}

@Composable
private fun TrainCueApp(
    isAmbient: Boolean,
    ambientUpdateToken: Int,
    burnInProtectionRequired: Boolean,
    resumeRequest: Int,
) {
    val context = LocalContext.current
    val repository = remember { TrainingRepository(context) }
    val cues = remember { TrainingCuePlayer(context) }
    val ongoingActivity = remember { TrainingOngoingActivity(context) }
    val screens = remember { mutableStateListOf(AppScreen.Splash) }

    var days by remember { mutableStateOf(emptyList<TrainingDay>()) }
    var completedSteps by remember { mutableStateOf(emptySet<String>()) }
    var completedDays by remember { mutableStateOf(emptySet<String>()) }
    var history by remember { mutableStateOf(emptyList<WorkoutLog>()) }
    var activeSession by remember { mutableStateOf<ActiveSession?>(null) }
    var selectedDayId by remember { mutableStateOf<String?>(null) }
    var selectedExercise by remember { mutableStateOf<SessionStep.Exercise?>(null) }
    var selectedRunMode by remember { mutableStateOf(RunMode.OUTDOOR) }
    var syncRequested by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("Plan saved offline") }
    var dataLoaded by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }

    val screen = screens.last()
    val selectedDay = days.firstOrNull { it.id == selectedDayId }
    val sessionDay = days.firstOrNull { it.id == activeSession?.dayId }

    fun push(target: AppScreen) {
        if (screens.lastOrNull() != target) screens.add(target)
    }

    fun replace(target: AppScreen) {
        if (screens.isNotEmpty()) screens.removeAt(screens.lastIndex)
        screens.add(target)
    }

    fun goHome() {
        screens.clear()
        screens.add(AppScreen.Home)
    }

    fun goBack() {
        if (screens.size > 1) screens.removeAt(screens.lastIndex)
    }

    fun saveSession(session: ActiveSession?) {
        activeSession = session
        repository.saveActiveSession(session)
    }

    fun saveCompleted(next: Set<String>) {
        completedSteps = next
        repository.saveCompletedSteps(next)
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startSession(day: TrainingDay) {
        ensureNotificationPermission()
        selectedDayId = day.id
        val existing = activeSession?.takeIf { it.dayId == day.id }
        if (existing == null) {
            val preCompleted = day.sessionSteps().associate { step ->
                step.key to if (step.key in completedSteps) step.requiredSets() else 0
            }.filterValues { it > 0 }
            val firstOpen = day.sessionSteps().indexOfFirst { it.key !in completedSteps }.let { if (it < 0) 0 else it }
            saveSession(
                ActiveSession(
                    dayId = day.id,
                    startedAt = System.currentTimeMillis(),
                    stepIndex = firstOpen,
                    completedSets = preCompleted,
                ),
            )
        }
        push(AppScreen.Session)
    }

    fun finishCurrentStep(step: SessionStep) {
        val session = activeSession ?: return
        val day = sessionDay ?: return
        val steps = day.sessionSteps()
        val currentSets = session.completedSets[step.key] ?: 0
        val nextSets = (currentSets + 1).coerceAtMost(step.requiredSets())
        val updatedSets = session.completedSets + (step.key to nextSets)
        if (nextSets >= step.requiredSets()) saveCompleted(completedSteps + step.key)
        val nextIndex = if (nextSets >= step.requiredSets()) session.stepIndex + 1 else session.stepIndex
        saveSession(session.copy(stepIndex = nextIndex.coerceAtMost(steps.size), completedSets = updatedSets))
        if (nextIndex >= steps.size) replace(AppScreen.Finish)
    }

    fun completeRun(distanceKm: Double, seconds: Long, mode: RunMode) {
        val session = activeSession ?: return
        val day = sessionDay ?: return
        val step = day.sessionSteps().getOrNull(session.stepIndex) ?: return
        saveCompleted(completedSteps + step.key)
        val nextIndex = session.stepIndex + 1
        saveSession(
            session.copy(
                stepIndex = nextIndex,
                completedSets = session.completedSets + (step.key to 1),
                runMode = mode,
                runDistanceKm = session.runDistanceKm + distanceKm,
                runSeconds = session.runSeconds + seconds,
            ),
        )
        cues.done("Run complete")
        replace(if (nextIndex >= day.sessionSteps().size) AppScreen.Finish else AppScreen.Session)
    }

    fun logSession(effort: Int) {
        val session = activeSession ?: return
        val day = sessionDay ?: return
        val steps = day.sessionSteps()
        val doneCount = steps.count { it.key in completedSteps }
        if (doneCount == steps.size && steps.isNotEmpty()) {
            completedDays = completedDays + day.id
            repository.saveCompletedDays(completedDays)
        }
        val now = System.currentTimeMillis()
        repository.addHistory(
            WorkoutLog(
                id = UUID.randomUUID().toString(),
                dayId = day.id,
                title = day.title,
                subtitle = day.subtitle,
                completedAt = now,
                durationSeconds = ((now - session.startedAt) / 1000).coerceAtLeast(1),
                completedSteps = doneCount,
                totalSteps = steps.size,
                effort = effort,
                runMode = session.runMode,
                distanceKm = session.runDistanceKm,
            ),
        )
        history = repository.loadHistory()
        saveSession(null)
        selectedDayId = null
        goHome()
    }

    LaunchedEffect(Unit) {
        days = repository.loadDays()
        completedSteps = repository.loadCompletedSteps()
        completedDays = repository.loadCompletedDays()
        history = repository.loadHistory()
        activeSession = repository.loadActiveSession()?.takeIf { saved -> days.any { it.id == saved.dayId } }
        if (days.isEmpty() && !repository.hasSavedPlan()) {
            days = starterPlan()
            repository.saveDays(days)
        }
        dataLoaded = true
        delay(850)
        if (screens.lastOrNull() == AppScreen.Splash) {
            screens.clear()
            screens.add(AppScreen.Home)
        }
    }

    LaunchedEffect(resumeRequest, dataLoaded) {
        if (resumeRequest > 0 && dataLoaded && activeSession != null) {
            selectedDayId = activeSession?.dayId
            screens.clear()
            screens.add(AppScreen.Session)
        }
    }

    LaunchedEffect(activeSession, sessionDay, notificationPermissionGranted) {
        val session = activeSession
        val day = sessionDay
        when {
            session == null -> ongoingActivity.cancel()
            day != null && notificationPermissionGranted -> runCatching { ongoingActivity.show(day, session) }
        }
    }

    LaunchedEffect(syncRequested) {
        if (!syncRequested) return@LaunchedEffect
        syncMessage = "Syncing plan..."
        runCatching { RemotePlanImporter().load() }
            .onSuccess { remoteDays ->
                if (remoteDays.isNotEmpty()) {
                    days = remoteDays
                    repository.saveDays(remoteDays)
                    syncMessage = "${remoteDays.size} days updated"
                } else {
                    syncMessage = "Plan is empty"
                }
            }
            .onFailure { syncMessage = it.syncMessage() }
        syncRequested = false
    }

    DisposableEffect(Unit) {
        onDispose { cues.release() }
    }

    BackHandler(enabled = screens.size > 1) { goBack() }

    TrainCueTheme {
        when (screen) {
            AppScreen.Splash -> SplashScreen()
            AppScreen.Home -> HomeScreen(
                nextDay = days.firstOrNull { !it.isComplete(completedSteps, completedDays) },
                completedCount = days.count { it.isComplete(completedSteps, completedDays) },
                totalCount = days.size,
                activeSession = activeSession,
                activeDay = days.firstOrNull { it.id == activeSession?.dayId },
                syncMessage = syncMessage,
                onStart = { day -> startSession(day) },
                onResume = { day -> ensureNotificationPermission(); selectedDayId = day.id; push(AppScreen.Session) },
                onCancelSession = { saveSession(null) },
                onPlan = { push(AppScreen.Plan) },
                onHistory = { push(AppScreen.History) },
                onSync = { syncRequested = true },
            )
            AppScreen.Plan -> PlanScreen(
                days = days,
                completedSteps = completedSteps,
                completedDays = completedDays,
                onSelect = { day -> selectedDayId = day.id; push(AppScreen.Day) },
                onBack = ::goBack,
            )
            AppScreen.Day -> selectedDay?.let { day ->
                DayScreen(
                    day = day,
                    completedSteps = completedSteps,
                    isCompleted = day.isComplete(completedSteps, completedDays),
                    canResume = activeSession?.dayId == day.id,
                    onStart = { startSession(day) },
                    onExercise = { block, workout ->
                        selectedExercise = SessionStep.Exercise(block, workout)
                        push(AppScreen.ExerciseDetail)
                    },
                    onBack = ::goBack,
                )
            }
            AppScreen.Session -> sessionDay?.let { day ->
                val session = activeSession
                val steps = day.sessionSteps()
                val step = session?.let { steps.getOrNull(it.stepIndex) }
                if (session != null && step != null) {
                    SessionScreen(
                        day = day,
                        step = step,
                        stepIndex = session.stepIndex,
                        stepCount = steps.size,
                        completedSets = session.completedSets[step.key] ?: 0,
                        isAmbient = isAmbient,
                        ambientUpdateToken = ambientUpdateToken,
                        burnInProtectionRequired = burnInProtectionRequired,
                        onComplete = { finishCurrentStep(step) },
                        onRun = { push(AppScreen.RunMode) },
                        onDetails = { exercise -> selectedExercise = exercise; push(AppScreen.ExerciseDetail) },
                        onPrevious = {
                            saveSession(session.copy(stepIndex = (session.stepIndex - 1).coerceAtLeast(0)))
                        },
                        onSkip = {
                            val nextIndex = session.stepIndex + 1
                            saveSession(session.copy(stepIndex = nextIndex))
                            if (nextIndex >= steps.size) replace(AppScreen.Finish)
                        },
                        onExit = ::goBack,
                    )
                } else if (session != null) {
                    FinishScreen(
                        completed = steps.count { it.key in completedSteps },
                        total = steps.size,
                        onRate = ::logSession,
                        onBack = ::goBack,
                    )
                }
            }
            AppScreen.RunMode -> sessionDay?.sessionSteps()?.getOrNull(activeSession?.stepIndex ?: -1)?.let { step ->
                RunModeScreen(
                    block = step.block,
                    onSelect = { mode ->
                        selectedRunMode = mode
                        if (mode == RunMode.MANUAL) {
                            completeRun(step.block.distanceKm ?: 0.0, 0, mode)
                        } else {
                            push(AppScreen.RunTracker)
                        }
                    },
                    onBack = ::goBack,
                )
            }
            AppScreen.RunTracker -> sessionDay?.sessionSteps()?.getOrNull(activeSession?.stepIndex ?: -1)?.let { step ->
                RunTrackerScreen(
                    block = step.block,
                    mode = selectedRunMode,
                    cues = cues,
                    onComplete = { distance, seconds -> completeRun(distance, seconds, selectedRunMode) },
                    onBack = ::goBack,
                )
            }
            AppScreen.ExerciseDetail -> selectedExercise?.let { exercise ->
                ExerciseDetailScreen(exercise = exercise, onBack = ::goBack)
            }
            AppScreen.Finish -> sessionDay?.let { day ->
                val steps = day.sessionSteps()
                FinishScreen(
                    completed = steps.count { it.key in completedSteps },
                    total = steps.size,
                    onRate = ::logSession,
                    onBack = ::goBack,
                )
            }
            AppScreen.History -> HistoryScreen(history = history, onBack = ::goBack)
        }
    }
}

private fun SessionStep.requiredSets(): Int = when (this) {
    is SessionStep.Exercise -> workout.sets
    is SessionStep.Run,
    is SessionStep.Simple -> 1
}

private fun starterPlan(): List<TrainingDay> = listOf(
    TrainingDay(
        id = "starter-day",
        title = "Starter Monday",
        subtitle = "Full body foundation",
        items = listOf(
            PlanItem(
                id = "starter-strength",
                type = "strength",
                label = "Foundation",
                workouts = listOf(
                    WorkoutItem(name = "Goblet squat", sets = 3, reps = "10", imageAsset = "goblet_squat"),
                    WorkoutItem(name = "Floor press", sets = 3, reps = "10", imageAsset = "kb_floor_press"),
                    WorkoutItem(name = "Easy mobility", sets = 1, reps = "5 min", imageAsset = "mobility"),
                ),
            ),
        ),
    ),
)
