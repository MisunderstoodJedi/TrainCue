package com.jongrady.traincue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal object TrainCueColors {
    val Background = Color(0xFF090B0D)
    val Surface = Color(0xFF171B1E)
    val SurfaceHigh = Color(0xFF22282C)
    val Mint = Color(0xFF62E6C1)
    val Coral = Color(0xFFFF806F)
    val Amber = Color(0xFFFFC857)
    val Ice = Color(0xFFE8F0F2)
    val Muted = Color(0xFF9EABB0)
    val Dim = Color(0xFF657176)
}

@Composable
internal fun TrainCueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = TrainCueColors.Mint,
            primaryVariant = Color(0xFF2BBE9B),
            secondary = TrainCueColors.Coral,
            secondaryVariant = Color(0xFFDA6557),
            background = TrainCueColors.Background,
            surface = TrainCueColors.Surface,
            error = TrainCueColors.Coral,
            onPrimary = Color(0xFF07130F),
            onSecondary = Color.Black,
            onBackground = TrainCueColors.Ice,
            onSurface = TrainCueColors.Ice,
            onError = Color.Black,
        ),
        content = content,
    )
}

@Composable
internal fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(TrainCueColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.traincue_training),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.28f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TrainMark()
            Spacer(Modifier.height(9.dp))
            Text("TrainCue", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("v${stringResource(R.string.app_version)}", fontSize = 10.sp, color = TrainCueColors.Muted)
        }
    }
}

@Composable
internal fun HomeScreen(
    nextDay: TrainingDay?,
    completedCount: Int,
    totalCount: Int,
    activeSession: ActiveSession?,
    activeDay: TrainingDay?,
    syncMessage: String,
    onStart: (TrainingDay) -> Unit,
    onResume: (TrainingDay) -> Unit,
    onCancelSession: () -> Unit,
    onPlan: () -> Unit,
    onHistory: () -> Unit,
    onSync: () -> Unit,
) {
    WatchList {
        item { Text("TRAINCUE V3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrainCueColors.Mint) }
        item { Text(if (activeDay != null) "In progress" else "Up next", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        if (activeDay != null && activeSession != null) {
            item {
                DayHeroChip(
                    day = activeDay,
                    kicker = "RESUME · STEP ${activeSession.stepIndex + 1}/${activeDay.sessionSteps().size}",
                    color = TrainCueColors.Amber,
                    onClick = { onResume(activeDay) },
                )
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(0.62f),
                    onClick = onCancelSession,
                    label = { Text("Cancel session") },
                    icon = { Icon(Icons.Default.Close, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        } else if (nextDay != null) {
            item {
                DayHeroChip(
                    day = nextDay,
                    kicker = "${nextDay.items.size} BLOCK${if (nextDay.items.size == 1) "" else "S"}",
                    color = TrainCueColors.Mint,
                    onClick = { onStart(nextDay) },
                )
            }
        } else {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    onClick = onPlan,
                    label = { Text("Plan complete") },
                    secondaryLabel = { Text("Choose any day to repeat") },
                    icon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                )
            }
        }
        item {
            val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
            Row(
                modifier = Modifier.fillMaxWidth(0.78f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(34.dp),
                        indicatorColor = TrainCueColors.Mint,
                        trackColor = TrainCueColors.SurfaceHigh,
                        strokeWidth = 4.dp,
                    )
                    Text("${(progress * 100).roundToInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("$completedCount of $totalCount days", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Plan progress", fontSize = 10.sp, color = TrainCueColors.Muted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HomeAction(Icons.Default.List, "Plan", onPlan)
                HomeAction(Icons.Default.History, "History", onHistory)
                HomeAction(Icons.Default.Refresh, "Sync", onSync)
            }
        }
        item { Text(syncMessage, fontSize = 10.sp, color = TrainCueColors.Dim, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun DayHeroChip(day: TrainingDay, kicker: String, color: Color, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(0.9f),
        onClick = onClick,
        label = { Text(day.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        secondaryLabel = { Text(day.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = kicker, tint = TrainCueColors.Background)
            }
        },
        colors = ChipDefaults.chipColors(backgroundColor = TrainCueColors.SurfaceHigh),
    )
}

@Composable
private fun HomeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            modifier = Modifier.size(46.dp),
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(backgroundColor = TrainCueColors.SurfaceHigh),
        ) {
            Icon(icon, contentDescription = label, tint = TrainCueColors.Ice)
        }
        Text(label, fontSize = 9.sp, color = TrainCueColors.Muted)
    }
}

@Composable
internal fun PlanScreen(
    days: List<TrainingDay>,
    completedSteps: Set<String>,
    completedDays: Set<String>,
    onSelect: (TrainingDay) -> Unit,
    onBack: () -> Unit,
) {
    WatchList {
        item { ScreenHeader("Training plan", onBack) }
        item { Text("${days.count { it.isComplete(completedSteps, completedDays) }} of ${days.size} complete", fontSize = 11.sp, color = TrainCueColors.Muted) }
        items(days, key = { it.id }) { day ->
            val done = day.isComplete(completedSteps, completedDays)
            Chip(
                modifier = Modifier.fillMaxWidth(0.9f),
                onClick = { onSelect(day) },
                label = { Text(day.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text(day.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = {
                    Icon(
                        if (done) Icons.Default.Check else typeIcon(day.items.firstOrNull()?.type),
                        contentDescription = if (done) "Completed" else null,
                        tint = if (done) TrainCueColors.Mint else typeColor(day.items.firstOrNull()?.type),
                    )
                },
                colors = ChipDefaults.chipColors(backgroundColor = if (done) Color(0xFF14241F) else TrainCueColors.Surface),
            )
        }
    }
}

@Composable
internal fun DayScreen(
    day: TrainingDay,
    completedSteps: Set<String>,
    isCompleted: Boolean,
    canResume: Boolean,
    onStart: () -> Unit,
    onExercise: (PlanItem, WorkoutItem) -> Unit,
    onBack: () -> Unit,
) {
    WatchList {
        item { ScreenHeader(day.title, onBack) }
        item { Text(day.subtitle, fontSize = 12.sp, color = TrainCueColors.Muted, textAlign = TextAlign.Center) }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.84f),
                onClick = onStart,
                label = { Text(if (canResume) "Resume workout" else if (isCompleted) "Repeat workout" else "Start workout") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        day.items.forEachIndexed { index, block ->
            item { BlockHeading(index + 1, block) }
            if (block.workouts.isEmpty()) {
                item { SimpleDetailRow(block.label, block.distanceKm?.let(::formatDistanceKm), block.completionKey() in completedSteps) }
            } else {
                items(block.workouts, key = { workout -> block.workoutCompletionKey(workout) }) { workout ->
                    ExerciseRow(
                        workout = workout,
                        complete = completedSteps.isWorkoutCompleted(block, workout) || block.completionKey() in completedSteps,
                        onClick = { onExercise(block, workout) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun BlockHeading(index: Int, block: PlanItem) {
    Row(
        modifier = Modifier.fillMaxWidth(0.82f).padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier.size(25.dp).clip(CircleShape).background(typeColor(block.type)),
            contentAlignment = Alignment.Center,
        ) {
            Text(index.toString(), fontSize = 10.sp, color = TrainCueColors.Background, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(block.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(block.type.uppercase(), fontSize = 9.sp, color = typeColor(block.type), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExerciseRow(workout: WorkoutItem, complete: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(8.dp))
            .background(TrainCueColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Thumbnail(workout.imageAsset, workout.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(workout.prescription(), fontSize = 10.sp, color = TrainCueColors.Muted, maxLines = 1)
        }
        Icon(
            if (complete) Icons.Default.Check else Icons.Default.ChevronRight,
            contentDescription = if (complete) "Complete" else "Details",
            tint = if (complete) TrainCueColors.Mint else TrainCueColors.Dim,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SimpleDetailRow(title: String, detail: String?, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(8.dp)).background(TrainCueColors.Surface).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (complete) Icons.Default.Check else Icons.Default.ChevronRight, contentDescription = null, tint = if (complete) TrainCueColors.Mint else TrainCueColors.Dim)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, fontSize = 12.sp)
            detail?.let { Text(it, fontSize = 10.sp, color = TrainCueColors.Muted) }
        }
    }
}

@Composable
internal fun SessionScreen(
    day: TrainingDay,
    step: SessionStep,
    stepIndex: Int,
    stepCount: Int,
    completedSets: Int,
    onComplete: () -> Unit,
    onRun: () -> Unit,
    onDetails: (SessionStep.Exercise) -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(TrainCueColors.Background)) {
        TimeText()
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 27.dp, end = 18.dp, bottom = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.size(28.dp), onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                }
                Text(
                    "${stepIndex + 1} / $stepCount",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = TrainCueColors.Muted,
                )
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Text("${((stepIndex + 1f) / stepCount * 100).roundToInt()}%", fontSize = 9.sp, color = TrainCueColors.Mint)
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (step) {
                    is SessionStep.Exercise -> {
                        val totalSets = step.workout.sets
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CompactExerciseImage(step.workout.imageAsset, step.workout.name) { onDetails(step) }
                            Text(step.block.label.uppercase(), fontSize = 8.sp, color = typeColor(step.block.type), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(step.workout.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(step.workout.prescription(), fontSize = 10.sp, color = TrainCueColors.Muted, textAlign = TextAlign.Center, maxLines = 1)
                            if (totalSets > 1) {
                                Text("Set ${(completedSets + 1).coerceAtMost(totalSets)} of $totalSets", fontSize = 10.sp, color = TrainCueColors.Amber)
                            }
                        }
                    }
                    is SessionStep.Run -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF173029)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = TrainCueColors.Mint, modifier = Modifier.size(30.dp))
                            }
                            Text(step.block.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                            Text(step.block.distanceKm?.let(::formatDistanceKm) ?: "Timed session", fontSize = 11.sp, color = TrainCueColors.Muted)
                            Text("${step.block.workouts.size} plan cues", fontSize = 9.sp, color = TrainCueColors.Dim)
                        }
                    }
                    is SessionStep.Simple -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(typeIcon(step.block.type), contentDescription = null, tint = typeColor(step.block.type), modifier = Modifier.size(46.dp))
                            Text(step.block.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.size(40.dp), enabled = stepIndex > 0, onClick = onPrevious) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous", tint = if (stepIndex > 0) TrainCueColors.Ice else TrainCueColors.Dim)
                }
                Button(
                    modifier = Modifier.size(52.dp),
                    onClick = if (step is SessionStep.Run) onRun else onComplete,
                    colors = ButtonDefaults.buttonColors(backgroundColor = TrainCueColors.Mint),
                ) {
                    Icon(if (step is SessionStep.Run) Icons.Default.PlayArrow else Icons.Default.Check, contentDescription = if (step is SessionStep.Run) "Start run" else "Complete", tint = TrainCueColors.Background)
                }
                IconButton(modifier = Modifier.size(40.dp), onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Skip", tint = TrainCueColors.Muted)
                }
            }
        }
    }
}

@Composable
internal fun RunModeScreen(block: PlanItem, onSelect: (RunMode) -> Unit, onBack: () -> Unit) {
    WatchList {
        item { ScreenHeader("Run setup", onBack) }
        item { Text(block.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        item { Text(block.distanceKm?.let(::formatDistanceKm) ?: "Timed run", fontSize = 12.sp, color = TrainCueColors.Muted) }
        if (block.workouts.isNotEmpty()) {
            item { Text("TODAY'S CUES", fontSize = 9.sp, color = TrainCueColors.Mint, fontWeight = FontWeight.Bold) }
            items(block.workouts) { cue ->
                Row(modifier = Modifier.fillMaxWidth(0.82f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(modifier = Modifier.padding(top = 5.dp).size(5.dp).clip(CircleShape).background(TrainCueColors.Mint))
                    Text(cue.name, modifier = Modifier.weight(1f), fontSize = 11.sp, color = TrainCueColors.Ice, maxLines = 3)
                }
            }
        }
        item { ModeChip(Icons.Default.Map, "Outdoor", "GPS distance and pace", TrainCueColors.Mint) { onSelect(RunMode.OUTDOOR) } }
        item { ModeChip(Icons.Default.Timer, "Treadmill", "Timer with manual finish", TrainCueColors.Amber) { onSelect(RunMode.TREADMILL) } }
        item { ModeChip(Icons.Default.Check, "No tracking", "Log this run as complete", TrainCueColors.Coral) { onSelect(RunMode.MANUAL) } }
    }
}

@Composable
private fun ModeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(0.88f),
        onClick = onClick,
        label = { Text(title) },
        secondaryLabel = { Text(subtitle) },
        icon = { Icon(icon, contentDescription = null, tint = color) },
        colors = ChipDefaults.chipColors(backgroundColor = TrainCueColors.Surface),
    )
}

@Composable
internal fun ExerciseDetailScreen(exercise: SessionStep.Exercise, onBack: () -> Unit) {
    WatchList {
        item { ScreenHeader("Exercise", onBack) }
        item { LargeExerciseImage(exercise.workout.imageAsset, exercise.workout.name, onClick = {}) }
        item { Text(exercise.workout.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.82f)) }
        item { Text(exercise.workout.prescription(), fontSize = 13.sp, color = TrainCueColors.Mint) }
        if (exercise.workout.note.isNotBlank()) {
            item { Text(exercise.workout.note, fontSize = 11.sp, color = TrainCueColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f)) }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.66f),
                onClick = onBack,
                label = { Text("Back") },
                icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
internal fun FinishScreen(completed: Int, total: Int, onRate: (Int) -> Unit, onBack: () -> Unit) {
    WatchList {
        item { ScreenHeader("Session complete", onBack) }
        item {
            Box(modifier = Modifier.size(70.dp).clip(CircleShape).background(Color(0xFF173029)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = TrainCueColors.Mint, modifier = Modifier.size(38.dp))
            }
        }
        item { Text("$completed of $total steps", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text("How did it feel?", fontSize = 12.sp, color = TrainCueColors.Muted) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffortButton("Easy", 2, TrainCueColors.Mint, onRate)
                EffortButton("Good", 3, TrainCueColors.Amber, onRate)
                EffortButton("Hard", 5, TrainCueColors.Coral, onRate)
            }
        }
    }
}

@Composable
private fun EffortButton(label: String, effort: Int, color: Color, onRate: (Int) -> Unit) {
    Button(modifier = Modifier.size(55.dp), onClick = { onRate(effort) }, colors = ButtonDefaults.buttonColors(backgroundColor = color)) {
        Text(label, fontSize = 10.sp, color = TrainCueColors.Background, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun HistoryScreen(history: List<WorkoutLog>, onBack: () -> Unit) {
    WatchList {
        item { ScreenHeader("History", onBack) }
        if (history.isEmpty()) {
            item { Icon(Icons.Default.History, contentDescription = null, tint = TrainCueColors.Dim, modifier = Modifier.size(42.dp)) }
            item { Text("No sessions yet", fontSize = 14.sp, color = TrainCueColors.Muted) }
        } else {
            items(history, key = { it.id }) { log -> HistoryRow(log) }
        }
    }
}

@Composable
private fun HistoryRow(log: WorkoutLog) {
    val date = rememberDate(log.completedAt)
    Column(
        modifier = Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(8.dp)).background(TrainCueColors.Surface).padding(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(log.title, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(effortLabel(log.effort), fontSize = 9.sp, color = effortColor(log.effort), fontWeight = FontWeight.Bold)
        }
        Text("$date · ${formatDuration(log.durationSeconds)}", fontSize = 10.sp, color = TrainCueColors.Muted)
        val runDetail = if (log.distanceKm > 0) " · ${formatDistanceKm(log.distanceKm)}" else ""
        Text("${log.completedSteps}/${log.totalSteps} steps$runDetail", fontSize = 10.sp, color = TrainCueColors.Dim)
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(0.88f), verticalAlignment = Alignment.CenterVertically) {
        IconButton(modifier = Modifier.size(34.dp), onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
        }
        Text(title, modifier = Modifier.weight(1f).padding(end = 34.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrainMark() {
    Box(
        modifier = Modifier.size(58.dp).clip(CircleShape).border(2.dp, TrainCueColors.Mint, CircleShape).background(Color(0xFF10201C)),
        contentAlignment = Alignment.Center,
    ) {
        Text("TC", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrainCueColors.Mint)
    }
}

@Composable
private fun Thumbnail(assetName: String?, description: String) {
    val resource = resolveDrawableResId(assetName)
    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)).background(TrainCueColors.SurfaceHigh), contentAlignment = Alignment.Center) {
        if (resource != null) {
            Image(painterResource(resource), description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = TrainCueColors.Dim, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LargeExerciseImage(assetName: String?, description: String, onClick: () -> Unit) {
    val resource = resolveDrawableResId(assetName)
    Box(
        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(TrainCueColors.SurfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (resource != null) {
            Image(painterResource(resource), description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
        } else {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = TrainCueColors.Dim, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun CompactExerciseImage(assetName: String?, description: String, onClick: () -> Unit) {
    val resource = resolveDrawableResId(assetName)
    Box(
        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(TrainCueColors.SurfaceHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (resource != null) {
            Image(painterResource(resource), description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = TrainCueColors.Dim, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun WatchList(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(TrainCueColors.Background)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 34.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
        PositionIndicator(scalingLazyListState = state)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(TrainCueColors.Background),
        )
        TimeText()
    }
}

@Composable
private fun resolveDrawableResId(assetName: String?): Int? {
    val context = LocalContext.current
    val cleaned = assetName?.trim().orEmpty()
    if (cleaned.isBlank()) return null
    return context.resources.getIdentifier(cleaned, "drawable", context.packageName).takeIf { it != 0 }
}

private fun WorkoutItem.prescription(): String {
    val setText = if (sets > 1) "$sets sets · " else ""
    return "$setText$reps"
}

private fun typeColor(type: String?): Color = when (type?.lowercase()) {
    "run" -> TrainCueColors.Mint
    "strength" -> TrainCueColors.Coral
    "hyrox", "mixed" -> TrainCueColors.Amber
    "rest", "recovery" -> Color(0xFF8FB7FF)
    else -> Color(0xFFB7A4FF)
}

private fun typeIcon(type: String?) = when (type?.lowercase()) {
    "run" -> Icons.Default.DirectionsRun
    "strength", "hyrox", "mixed" -> Icons.Default.FitnessCenter
    "rest", "recovery" -> Icons.Default.Timer
    else -> Icons.Default.ChevronRight
}

internal fun formatDistanceKm(km: Double): String = if (km < 10) {
    "${(km * 10).roundToInt() / 10.0} km"
} else {
    "${km.roundToInt()} km"
}

internal fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${remaining.toString().padStart(2, '0')}" else "$minutes:${remaining.toString().padStart(2, '0')}"
}

private fun rememberDate(timestamp: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))

private fun effortLabel(effort: Int): String = when {
    effort <= 2 -> "EASY"
    effort >= 5 -> "HARD"
    else -> "GOOD"
}

private fun effortColor(effort: Int): Color = when {
    effort <= 2 -> TrainCueColors.Mint
    effort >= 5 -> TrainCueColors.Coral
    else -> TrainCueColors.Amber
}
