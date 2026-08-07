package com.jongrady.traincue

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import kotlin.math.roundToInt

private const val ROUTINE_FEED_URL = "https://raw.githubusercontent.com/MisunderstoodJedi/TrainCue/main/routines.json"
private const val MILES_TO_KM = 1.609344

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrainCueApp()
        }
    }
}

private data class TrainingDay(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<PlanItem>,
)

private data class PlanItem(
    val id: String,
    val type: String,
    val label: String,
    val distanceKm: Double? = null,
    val workouts: List<WorkoutItem> = emptyList(),
)

private data class WorkoutItem(
    val id: String? = null,
    val name: String,
    val sets: Int,
    val reps: String,
    val note: String = "",
    val imageAsset: String? = null,
)

private data class WorkoutSelection(
    val item: PlanItem,
    val workout: WorkoutItem,
)

private enum class Screen {
    Splash,
    Plan,
    Day,
    RunOptions,
    RunTracker,
    ExercisePreview,
}

@Composable
private fun TrainCueApp() {
    val context = LocalContext.current
    val repository = remember { TrainingRepository(context) }
    val cues = remember { TrainingCuePlayer(context) }
    val days = remember { mutableStateListOf<TrainingDay>() }
    val completedItems = remember { mutableStateMapOf<String, Boolean>() }
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var activeDay by remember { mutableStateOf<TrainingDay?>(null) }
    var activeRun by remember { mutableStateOf<PlanItem?>(null) }
    var activeWorkoutSelection by remember { mutableStateOf<WorkoutSelection?>(null) }
    var syncMessage by rememberSaveable { mutableStateOf("Local plan") }
    var updateRequested by remember { mutableStateOf(false) }
    var confirmDeleteDay by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1600)
        if (screen == Screen.Splash) screen = Screen.Plan
    }

    LaunchedEffect(Unit) {
        days.clear()
        days.addAll(repository.load())
        completedItems.clear()
        repository.loadCompleted().forEach { completedItems[it] = true }

        if (days.isEmpty() && !repository.hasSavedPlan()) {
            days.addAll(starterPlan())
            repository.save(days)
        }
    }

    LaunchedEffect(updateRequested) {
        if (updateRequested && ROUTINE_FEED_URL.isNotBlank()) {
            syncMessage = "Syncing..."
            runCatching { RemotePlanImporter(ROUTINE_FEED_URL).load() }
                .onSuccess { remoteDays ->
                    val visibleRemoteDays = remoteDays.filterNot { it.id in repository.loadDeletedDayIds() }
                    if (visibleRemoteDays.isNotEmpty()) {
                        days.clear()
                        days.addAll(visibleRemoteDays)
                        repository.save(days)
                        syncMessage = "Synced from GitHub"
                    } else if (remoteDays.isNotEmpty()) {
                        days.clear()
                        repository.save(days)
                        syncMessage = "Synced: all days done"
                    } else {
                        syncMessage = "GitHub file empty"
                    }
                }
                .onFailure { error ->
                    syncMessage = error.syncMessage()
                }
            updateRequested = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { cues.release() }
    }

    fun setCompleted(key: String, completed: Boolean) {
        if (completed) completedItems[key] = true else completedItems.remove(key)
        repository.saveCompleted(completedItems.keys)
    }

    fun toggleCompleted(key: String) {
        setCompleted(key, completedItems[key] != true)
    }

    MaterialTheme {
        AppBackdrop {
            TimeText()
            when (screen) {
                Screen.Splash -> TrainCueSplash()
                Screen.Plan -> PlanScreen(
                    days = days,
                    syncMessage = syncMessage,
                    onSelect = {
                        activeDay = it
                        screen = Screen.Day
                    },
                    onUpdate = { updateRequested = true },
                )
                Screen.Day -> activeDay?.let { day ->
                    DayScreen(
                        day = day,
                        completedItems = completedItems,
                        onRun = {
                            activeRun = it
                            screen = Screen.RunOptions
                        },
                        onToggleItem = { item -> toggleCompleted(item.completionKey()) },
                        onToggleWorkout = { item, workout ->
                            if (workout.imageAsset.isNullOrBlank()) {
                                val keys = item.workoutCompletionKeys(workout)
                                if (completedItems.isWorkoutCompleted(item, workout)) {
                                    keys.forEach { setCompleted(it, false) }
                                } else {
                                    setCompleted(keys.first(), true)
                                }
                            } else {
                                activeWorkoutSelection = WorkoutSelection(item, workout)
                                screen = Screen.ExercisePreview
                            }
                        },
                        confirmDelete = confirmDeleteDay,
                        onDeleteRequest = { confirmDeleteDay = true },
                        onDeleteCancel = { confirmDeleteDay = false },
                        onDeleteConfirm = {
                            days.remove(day)
                            repository.addDeletedDayId(day.id)
                            repository.save(days)
                            activeDay = null
                            confirmDeleteDay = false
                            screen = Screen.Plan
                        },
                        onBack = {
                            confirmDeleteDay = false
                            screen = Screen.Plan
                        },
                    )
                }
                Screen.RunOptions -> activeRun?.let { run ->
                    RunOptionsScreen(
                        run = run,
                        onStart = { screen = Screen.RunTracker },
                        onManualDone = {
                            setCompleted(run.completionKey(), true)
                            screen = Screen.Day
                        },
                        onBack = { screen = Screen.Day },
                    )
                }
                Screen.RunTracker -> activeRun?.let { run ->
                    RunTrackerScreen(
                        run = run,
                        cues = cues,
                        onComplete = {
                            setCompleted(run.completionKey(), true)
                            screen = Screen.Day
                        },
                        onBack = { screen = Screen.RunOptions },
                    )
                }
                Screen.ExercisePreview -> activeWorkoutSelection?.let { selection ->
                    ExercisePreviewScreen(
                        workout = selection.workout,
                        isCompleted = completedItems.isWorkoutCompleted(selection.item, selection.workout),
                        onToggleCompleted = {
                            val keys = selection.item.workoutCompletionKeys(selection.workout)
                            if (completedItems.isWorkoutCompleted(selection.item, selection.workout)) {
                                keys.forEach { setCompleted(it, false) }
                            } else {
                                setCompleted(keys.first(), true)
                            }
                            activeWorkoutSelection = null
                            screen = Screen.Day
                        },
                        onClose = {
                            activeWorkoutSelection = null
                            screen = Screen.Day
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBackdrop(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.traincue_training),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.86f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x55050607), Color(0xCC050607), Color(0xFA050607)),
                    ),
                ),
        )
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TrainCueSplash() {
    val versionName = stringResource(id = R.string.app_version)
    CenterColumn {
        TrainMark()
        Spacer(Modifier.height(8.dp))
        Text("TrainCue", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Run. Lift. Complete.", fontSize = 12.sp, color = Color(0xFFCFD8DC))
        Spacer(Modifier.height(6.dp))
        Text("v$versionName", fontSize = 10.sp, color = Color(0xFF90A4AE))
    }
}

@Composable
private fun PlanScreen(
    days: List<TrainingDay>,
    syncMessage: String,
    onSelect: (TrainingDay) -> Unit,
    onUpdate: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 34.dp, bottom = 28.dp),
    ) {
        item { TrainMark() }
        item { Text("TrainCue", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item { Text(syncMessage, fontSize = 11.sp, color = Color(0xFFB0BEC5)) }
        items(days, key = { it.id }) { day ->
            Chip(
                modifier = Modifier.fillMaxWidth(0.88f),
                onClick = { onSelect(day) },
                label = { Text(day.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text("${day.subtitle}  ${day.items.size} items") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.68f),
                onClick = onUpdate,
                label = { Text("Update") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
private fun DayScreen(
    day: TrainingDay,
    completedItems: Map<String, Boolean>,
    onRun: (PlanItem) -> Unit,
    onToggleItem: (PlanItem) -> Unit,
    onToggleWorkout: (PlanItem, WorkoutItem) -> Unit,
    confirmDelete: Boolean,
    onDeleteRequest: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 34.dp, bottom = 30.dp),
    ) {
        item { Text(day.title, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { Text(day.subtitle, fontSize = 12.sp, color = Color(0xFFCFD8DC)) }
        day.items.forEach { item ->
            when (item.type.lowercase()) {
                "run" -> item {
                    PlanItemRow(
                        item = item,
                        isCompleted = completedItems[item.completionKey()] == true,
                        onClick = { onRun(item) },
                    )
                }
                "strength" -> {
                    item {
                        PlanItemRow(
                            item = item,
                            isCompleted = item.workouts.isNotEmpty() && item.workouts.all { workout ->
                                completedItems.isWorkoutCompleted(item, workout)
                            },
                            onClick = { },
                        )
                    }
                    items(item.workouts) { workout ->
                        WorkoutRow(
                            workout = workout,
                            isCompleted = completedItems.isWorkoutCompleted(item, workout),
                            onToggle = { onToggleWorkout(item, workout) },
                        )
                    }
                }
                else -> item {
                    PlanItemRow(
                        item = item,
                        isCompleted = completedItems[item.completionKey()] == true,
                        onClick = { onToggleItem(item) },
                    )
                }
            }
        }
        item {
            if (confirmDelete) {
                Text("Delete day?", fontSize = 13.sp, color = Color(0xFFFFCC80))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.size(52.dp),
                        onClick = onDeleteCancel,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF37474F)),
                    ) {
                        Text("No", fontSize = 13.sp)
                    }
                    Button(
                        modifier = Modifier.size(52.dp),
                        onClick = onDeleteConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF5D2C2C)),
                    ) {
                        Text("Yes", fontSize = 12.sp)
                    }
                }
            } else {
                Chip(
                    modifier = Modifier.fillMaxWidth(0.68f),
                    onClick = onDeleteRequest,
                    label = { Text("Delete day") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.62f),
                onClick = onBack,
                label = { Text("Plan") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
private fun PlanItemRow(item: PlanItem, isCompleted: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isCompleted) Color(0xCC123D2F) else Color(0xB0101820)
    val borderColor = if (isCompleted) Color(0xCC1DE9B6) else Color(0x441DE9B6)
    val typeText = item.type.lowercase().replaceFirstChar { it.uppercase() }
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor, RoundedCornerShape(18.dp))
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(typeText, fontSize = 10.sp, color = Color(0xFFB0BEC5))
        }
        Text(
            if (isCompleted) "Done" else item.distanceKm?.let { "${formatDistanceKm(it)}" } ?: "",
            textAlign = TextAlign.End,
            fontSize = 12.sp,
            color = Color(0xFF1DE9B6),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WorkoutRow(workout: WorkoutItem, isCompleted: Boolean, onToggle: () -> Unit) {
    val backgroundColor = if (isCompleted) Color(0xCC123D2F) else Color(0x88101820)
    val repsColor = if (isCompleted) Color(0xFFA5D6A7) else Color(0xFFCFD8DC)
    val statusText = when {
        isCompleted -> "Done"
        !workout.imageAsset.isNullOrBlank() -> "Preview"
        else -> "${workout.sets} x ${workout.reps}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(workout.name, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(statusText, fontSize = 12.sp, color = repsColor, maxLines = 1)
    }
}

@Composable
private fun ExercisePreviewScreen(
    workout: WorkoutItem,
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val imageResId = remember(workout.imageAsset) {
        resolveDrawableResId(context, workout.imageAsset)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 28.dp, end = 18.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            workout.name,
            modifier = Modifier.fillMaxWidth(0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text("${workout.sets} x ${workout.reps}", fontSize = 11.sp, color = Color(0xFFCFD8DC))
        if (workout.note.isNotBlank()) {
            Text(
                workout.note,
                modifier = Modifier.fillMaxWidth(0.9f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xEE101820), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0x661DE9B6), RoundedCornerShape(14.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imageResId != null) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = workout.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("No image", fontSize = 12.sp, color = Color(0xFFFFCC80))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.size(48.dp),
                onClick = onToggleCompleted,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isCompleted) Color(0xFF546E7A) else Color(0xFF1B5E48),
                ),
            ) {
                Text(if (isCompleted) "Undo" else "Done", fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            Button(
                modifier = Modifier.size(48.dp),
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF37474F)),
            ) {
                Text("Back", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RunOptionsScreen(run: PlanItem, onStart: () -> Unit, onManualDone: () -> Unit, onBack: () -> Unit) {
    CenterColumn {
        TrainMark()
        Spacer(Modifier.height(8.dp))
        Text(run.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(run.distanceKm?.let { formatDistanceKm(it) } ?: "Manual run", fontSize = 13.sp, color = Color(0xFFCFD8DC))
        Spacer(Modifier.height(10.dp))
        Chip(onClick = onStart, label = { Text("Track outside") }, colors = ChipDefaults.primaryChipColors())
        Chip(onClick = onManualDone, label = { Text("Treadmill done") }, colors = ChipDefaults.secondaryChipColors())
        Chip(modifier = Modifier.fillMaxWidth(0.62f), onClick = onBack, label = { Text("Back") }, colors = ChipDefaults.secondaryChipColors())
    }
}

@Composable
private fun RunTrackerScreen(run: PlanItem, cues: TrainingCuePlayer, onComplete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val targetKm = run.distanceKm ?: 0.0
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var distanceMeters by remember { mutableStateOf(0.0) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var currentAccuracy by remember { mutableStateOf<Float?>(null) }
    var lastAnnouncedKm by remember { mutableStateOf(0) }
    var halfwayAnnounced by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) isTracking = true
    }

    DisposableEffect(hasPermission, isTracking) {
        if (!hasPermission || !isTracking) return@DisposableEffect onDispose { }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location ->
            currentAccuracy = location.accuracy
            val previous = lastLocation
            if (previous != null && location.accuracy <= 80f) {
                val delta = previous.distanceTo(location).toDouble()
                if (delta in 0.0..250.0) distanceMeters += delta
            }
            lastLocation = location
        }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, listener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
        }
        onDispose { locationManager.removeUpdates(listener) }
    }

    LaunchedEffect(isTracking) {
        if (isTracking) cues.speak("Run started")
    }

    LaunchedEffect(isTracking) {
        while (isTracking) {
            delay(1000)
            elapsedSeconds += 1
        }
    }

    LaunchedEffect(distanceMeters, completed) {
        if (!completed && targetKm > 0.0) {
            val currentKm = (distanceMeters / 1000.0).toInt()
            if (currentKm > lastAnnouncedKm) {
                lastAnnouncedKm = currentKm
                cues.mark("${currentKm} kilometre")
            }
            if (!halfwayAnnounced && targetKm < 2.0 && distanceMeters >= targetKm * 500.0) {
                halfwayAnnounced = true
                cues.mark("Halfway")
            }
            if (distanceMeters >= targetKm * 1000.0) {
                completed = true
                isTracking = false
                cues.done("Run complete")
                onComplete()
            }
        }
    }

    CenterColumn {
        Text("Outdoor run", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(run.label, fontSize = 12.sp, color = Color(0xFFCFD8DC), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("${formatDistanceKm(distanceMeters / 1000.0)}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Target ${formatDistanceKm(targetKm)}", fontSize = 12.sp, color = Color(0xFFB0BEC5))
        Text(
            "${formatElapsed(elapsedSeconds)}  ${formatAveragePace(elapsedSeconds, distanceMeters)}",
            fontSize = 11.sp,
            color = Color(0xFFCFD8DC),
        )
        Text(
            runStatusText(hasPermission, isTracking, currentAccuracy),
            fontSize = 10.sp,
            color = Color(0xFF90A4AE),
        )
        Spacer(Modifier.height(10.dp))
        if (!hasPermission) {
            Chip(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, label = { Text("Allow GPS") })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.size(54.dp), onClick = { isTracking = !isTracking }) {
                    Text(if (isTracking) "Pause" else "Start", fontSize = 11.sp)
                }
                Button(
                    modifier = Modifier.size(54.dp),
                    onClick = {
                        cues.done("Run complete")
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E4E3D)),
                ) {
                    Text("Done", fontSize = 12.sp)
                }
            }
        }
        Chip(modifier = Modifier.fillMaxWidth(0.58f), onClick = onBack, label = { Text("Back") }, colors = ChipDefaults.secondaryChipColors())
    }
}

@Composable
private fun TrainMark() {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .border(1.dp, Color(0x881DE9B6), RoundedCornerShape(29.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = painterResource(id = R.drawable.traincue_training), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Color(0x66050607)))
        Text("TC", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DE9B6))
    }
}

@Composable
private fun CenterColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

private fun starterPlan(): List<TrainingDay> {
    return listOf(
        TrainingDay("week1-tuesday", "Week 1 Tuesday", "Easy run", listOf(PlanItem("w1-tue-run", "run", "2 mi run", distanceKm = 2 * MILES_TO_KM))),
        TrainingDay(
            "week1-thursday",
            "Week 1 Thursday",
            "Run + strength",
            listOf(
                PlanItem("w1-thu-run", "run", "2 mi run", distanceKm = 2 * MILES_TO_KM),
                PlanItem(
                    "w1-thu-strength",
                    "strength",
                    "Upper body strength",
                    workouts = listOf(
                        WorkoutItem(id = "bench-press", name = "Bench Press", sets = 3, reps = "10", imageAsset = "bench_press"),
                        WorkoutItem(id = "shoulder-press", name = "Shoulder Press", sets = 3, reps = "8-10", imageAsset = "shoulder_press"),
                    ),
                ),
            ),
        ),
    )
}

private class RemotePlanImporter(private val feedUrl: String) {
    suspend fun load(): List<TrainingDay> = withContext(Dispatchers.IO) {
        val raw = try {
            val connection = URL(feedUrl.withCacheBuster()).openConnection() as HttpURLConnection
            connection.run {
                connectTimeout = 8000
                readTimeout = 8000
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
        return "${this}${separator}updated=${System.currentTimeMillis()}"
    }
}

private class PlanSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun Throwable.syncMessage(): String {
    return when (this) {
        is PlanSyncException -> message ?: "GitHub sync failed"
        is JSONException -> "Invalid plan JSON"
        is SocketTimeoutException -> "GitHub timeout"
        is UnknownHostException -> "No internet"
        is IOException -> "Network error"
        else -> "GitHub sync failed"
    }
}

private class TrainingRepository(context: Context) {
    private val prefs = context.getSharedPreferences("train_plan", Context.MODE_PRIVATE)

    fun load(): List<TrainingDay> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return parseDays(raw)
    }

    fun hasSavedPlan(): Boolean {
        return prefs.contains("items")
    }

    fun save(days: List<TrainingDay>) {
        prefs.edit().putString("items", days.toJsonArray().toString()).apply()
    }

    fun loadCompleted(): Set<String> {
        return prefs.getStringSet("completed", emptySet()).orEmpty()
    }

    fun saveCompleted(completed: Set<String>) {
        prefs.edit().putStringSet("completed", completed.toSet()).apply()
    }

    fun loadDeletedDayIds(): Set<String> {
        return prefs.getStringSet("deleted_day_ids", emptySet()).orEmpty()
    }

    fun addDeletedDayId(dayId: String) {
        prefs.edit().putStringSet("deleted_day_ids", loadDeletedDayIds() + dayId).apply()
    }
}

private class TrainingCuePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private var ttsReady = false
    private val tts = TextToSpeech(appContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }

    init {
        tts.language = Locale.getDefault()
        tts.setSpeechRate(1.0f)
    }

    fun mark(spokenText: String) {
        vibrate(longArrayOf(0, 130, 70, 130))
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
        speak(spokenText)
    }

    fun done(spokenText: String) {
        vibrate(longArrayOf(0, 220, 80, 220, 80, 320))
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 350)
        speak(spokenText)
    }

    fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "traincue-${System.currentTimeMillis()}")
    }

    fun release() {
        tone.release()
        tts.stop()
        tts.shutdown()
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Activity.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

private fun PlanItem.completionKey(): String = id

private fun PlanItem.workoutCompletionKey(workout: WorkoutItem): String {
    return workout.id?.takeIf { it.isNotBlank() }?.let { "${id}:${it}" } ?: legacyWorkoutCompletionKey(workout)
}

private fun PlanItem.workoutCompletionKeys(workout: WorkoutItem): List<String> {
    return listOf(workoutCompletionKey(workout), legacyWorkoutCompletionKey(workout)).distinct()
}

private fun Map<String, Boolean>.isWorkoutCompleted(item: PlanItem, workout: WorkoutItem): Boolean {
    return item.workoutCompletionKeys(workout).any { this[it] == true }
}

private fun PlanItem.legacyWorkoutCompletionKey(workout: WorkoutItem): String {
    return "${id}:${workout.name.trim().uppercase()}"
}

private fun parseDays(raw: String): List<TrainingDay> {
    val trimmed = raw.trim()
    val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("routines")
    return List(array.length()) { index -> array.getJSONObject(index).toTrainingDay() }
}

private fun JSONObject.toTrainingDay(): TrainingDay {
    val itemsArray = getJSONArray("items")
    return TrainingDay(
        id = getString("id"),
        title = getString("title"),
        subtitle = optString("subtitle", ""),
        items = List(itemsArray.length()) { index -> itemsArray.getJSONObject(index).toPlanItem() },
    )
}

private fun JSONObject.toPlanItem(): PlanItem {
    val distanceKm = when {
        has("distanceKm") -> getDouble("distanceKm")
        has("distanceMiles") -> getDouble("distanceMiles") * MILES_TO_KM
        else -> null
    }
    val workoutsArray = optJSONArray("workouts")
    return PlanItem(
        id = getString("id"),
        type = getString("type"),
        label = getString("label"),
        distanceKm = distanceKm,
        workouts = if (workoutsArray == null) emptyList() else List(workoutsArray.length()) { index -> workoutsArray.getJSONObject(index).toWorkoutItem() },
    )
}

private fun JSONObject.toWorkoutItem(): WorkoutItem {
    return WorkoutItem(
        id = optString("id").takeIf { it.isNotBlank() },
        name = getString("name"),
        sets = getInt("sets").coerceAtLeast(1),
        reps = get("reps").toString(),
        note = optString("note", ""),
        imageAsset = optString("imageAsset").takeIf { it.isNotBlank() },
    )
}

private fun List<TrainingDay>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { day -> array.put(day.toJson()) }
    return array
}

private fun TrainingDay.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("items", JSONArray().also { array -> items.forEach { array.put(it.toJson()) } })
}

private fun PlanItem.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("type", type)
        .put("label", label)
        .also { json ->
            distanceKm?.let { json.put("distanceKm", it) }
            if (workouts.isNotEmpty()) json.put("workouts", JSONArray().also { array -> workouts.forEach { array.put(it.toJson()) } })
        }
}

private fun WorkoutItem.toJson(): JSONObject {
    return JSONObject()
        .also { json -> id?.takeIf { it.isNotBlank() }?.let { json.put("id", it) } }
        .put("name", name)
        .put("sets", sets)
        .put("reps", reps)
        .put("note", note)
        .also { json -> imageAsset?.takeIf { it.isNotBlank() }?.let { json.put("imageAsset", it) } }
}

private fun formatDistanceKm(km: Double): String {
    return if (km < 10) "${((km * 10).roundToInt() / 10.0)} km" else "${km.roundToInt()} km"
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
}

private fun formatAveragePace(seconds: Long, distanceMeters: Double): String {
    if (seconds <= 0 || distanceMeters < 50.0) return "--:--/km"
    val paceSeconds = (seconds / (distanceMeters / 1000.0)).roundToInt().coerceAtLeast(1)
    return "${paceSeconds / 60}:${(paceSeconds % 60).toString().padStart(2, '0')}/km"
}

private fun runStatusText(hasPermission: Boolean, isTracking: Boolean, accuracy: Float?): String {
    if (!hasPermission) return "GPS permission needed"
    if (!isTracking) return "Paused"
    return accuracy?.let { "GPS +/- ${it.roundToInt()} m" } ?: "Finding GPS"
}

private fun resolveDrawableResId(context: Context, assetName: String?): Int? {
    val cleaned = assetName?.trim().orEmpty()
    if (cleaned.isBlank()) return null
    val resId = context.resources.getIdentifier(cleaned, "drawable", context.packageName)
    return resId.takeIf { it != 0 }
}

private fun String?.prettyAssetLabel(): String {
    val cleaned = this?.trim().orEmpty()
    if (cleaned.isBlank()) return "Drawable asset"
    return cleaned.replace('_', ' ').split(Regex("\\s+")).joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
