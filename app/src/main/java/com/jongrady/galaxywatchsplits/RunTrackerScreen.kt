package com.jongrady.traincue

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun RunTrackerScreen(
    block: PlanItem,
    mode: RunMode,
    cues: TrainingCuePlayer,
    onComplete: (distanceKm: Double, seconds: Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val targetKm = block.distanceKm ?: block.workouts.mapNotNull { it.distanceKm }.maxOrNull() ?: 0.0
    var hasPermission by remember {
        mutableStateOf(
            mode != RunMode.OUTDOOR || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var distanceMeters by remember { mutableStateOf(0.0) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var tracking by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var lastAnnouncedKm by remember { mutableStateOf(0) }
    var halfwayAnnounced by remember { mutableStateOf(false) }
    var autoCompleted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) tracking = true
    }

    DisposableEffect(tracking) {
        val activity = context as? Activity
        if (tracking) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(hasPermission, tracking, mode) {
        if (!hasPermission || !tracking || mode != RunMode.OUTDOOR) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location ->
            accuracy = location.accuracy
            val previous = lastLocation
            if (previous != null && location.accuracy <= 60f) {
                val delta = previous.distanceTo(location).toDouble()
                if (delta in 1.0..120.0) distanceMeters += delta
            }
            lastLocation = location
        }
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 2f, listener)
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2_000L, 5f, listener)
        }
        onDispose { manager.removeUpdates(listener) }
    }

    LaunchedEffect(tracking) {
        if (tracking) cues.speak(if (elapsedSeconds == 0L) "Workout started" else "Workout resumed")
        while (tracking) {
            delay(1_000)
            elapsedSeconds++
        }
    }

    LaunchedEffect(distanceMeters, autoCompleted) {
        if (mode != RunMode.OUTDOOR || !tracking || autoCompleted) return@LaunchedEffect
        val currentKm = (distanceMeters / 1_000).toInt()
        if (currentKm > lastAnnouncedKm) {
            lastAnnouncedKm = currentKm
            cues.mark("$currentKm kilometre")
        }
        if (!halfwayAnnounced && targetKm > 0 && distanceMeters >= targetKm * 500) {
            halfwayAnnounced = true
            cues.mark("Halfway")
        }
        if (targetKm > 0 && distanceMeters >= targetKm * 1_000) {
            autoCompleted = true
            tracking = false
            onComplete(distanceMeters / 1_000, elapsedSeconds)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TrainCueColors.Background)) {
        TimeText()
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.size(32.dp), onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(19.dp))
                }
                Text(
                    if (mode == RunMode.OUTDOOR) "OUTDOOR" else "TREADMILL",
                    modifier = Modifier.weight(1f).padding(end = 32.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = if (mode == RunMode.OUTDOOR) TrainCueColors.Mint else TrainCueColors.Amber,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (mode == RunMode.OUTDOOR) formatDistanceKm(distanceMeters / 1_000) else formatDuration(elapsedSeconds),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (mode == RunMode.OUTDOOR) formatDuration(elapsedSeconds) else targetKm.takeIf { it > 0 }?.let { "Target ${formatDistanceKm(it)}" } ?: block.label,
                    fontSize = 13.sp,
                    color = TrainCueColors.Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                if (mode == RunMode.OUTDOOR) {
                    Text(averagePace(elapsedSeconds, distanceMeters), fontSize = 12.sp, color = TrainCueColors.Ice)
                    Text(gpsStatus(hasPermission, tracking, accuracy), fontSize = 10.sp, color = statusColor(hasPermission, accuracy))
                } else {
                    Text(if (tracking) "Timer running" else "Timer paused", fontSize = 10.sp, color = TrainCueColors.Dim)
                }
            }

            if (!hasPermission) {
                Button(
                    modifier = Modifier.size(62.dp),
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = TrainCueColors.Mint),
                ) {
                    Text("GPS", fontSize = 11.sp, color = TrainCueColors.Background, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        modifier = Modifier.size(58.dp),
                        onClick = { tracking = !tracking },
                        colors = ButtonDefaults.buttonColors(backgroundColor = if (tracking) TrainCueColors.Amber else TrainCueColors.Mint),
                    ) {
                        Icon(if (tracking) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (tracking) "Pause" else "Start", tint = TrainCueColors.Background)
                    }
                    Button(
                        modifier = Modifier.size(58.dp),
                        onClick = {
                            tracking = false
                            val loggedDistance = if (mode == RunMode.TREADMILL) targetKm else distanceMeters / 1_000
                            onComplete(loggedDistance, elapsedSeconds)
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = TrainCueColors.Coral),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Finish", tint = TrainCueColors.Background)
                    }
                }
            }
        }
    }
}

private fun averagePace(seconds: Long, distanceMeters: Double): String {
    if (seconds <= 0 || distanceMeters < 50) return "--:-- /km"
    val pace = (seconds / (distanceMeters / 1_000)).roundToInt().coerceAtLeast(1)
    return "${pace / 60}:${(pace % 60).toString().padStart(2, '0')} /km"
}

private fun gpsStatus(hasPermission: Boolean, tracking: Boolean, accuracy: Float?): String = when {
    !hasPermission -> "Location permission needed"
    !tracking -> "Paused"
    accuracy == null -> "Finding GPS"
    else -> "GPS +/- ${accuracy.roundToInt()} m"
}

private fun statusColor(hasPermission: Boolean, accuracy: Float?): Color = when {
    !hasPermission -> TrainCueColors.Coral
    accuracy == null -> TrainCueColors.Amber
    accuracy <= 30 -> TrainCueColors.Mint
    else -> TrainCueColors.Amber
}
