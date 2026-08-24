package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.data.repo.formatTimeGerman
import com.earendil.todonotes.data.repo.nowMs
import com.earendil.todonotes.ui.BackHandler

/**
 * Detail-Ansicht eines Zufriedenheits-Trackers:
 * - Liniengrafik des Verlaufs (0–10) über die Zeit (reines Canvas, kein Chart-Library)
 * - Liste der Änderungen ("von 3 auf 4")
 * - +/− Buttons zum direkten Anpassen
 * - Zahnrad (Ecke) → HabitEditDialog zum Bearbeiten
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    habit: Habit,
    history: List<HabitHistoryEntry>,
    onRatingChange: (String, Int) -> Unit,
    onEditHabit: (String, HabitFormData) -> Unit,
    onBack: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    val rating = habit.currentRating ?: 0

    // Graph-Punkte: Startwert (erste Änderung: count = alter Wert) + jede Änderung.
    // chronologisch aufsteigend.
    val chron = remember(history) { history.sortedBy { it.loggedAt } }
    val points = remember(chron, rating) {
        buildList {
            val first = chron.firstOrNull()
            if (first != null) {
                add(first.loggedAt to (first.count))   // Startwert
            }
            chron.forEach { add(it.loggedAt to (it.newRating ?: it.count)) }
            if (isEmpty()) add(nowMs() to rating)
        }
    }

    BackHandler(enabled = true) { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            habit.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                // Zahnrad in der Ecke → bearbeiten
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Bearbeiten")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Aktueller Wert groß + +/−
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$rating",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    " / 10",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = { onRatingChange(habit.id, -1) },
                    enabled = rating > 0
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Weniger")
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { onRatingChange(habit.id, +1) },
                    enabled = rating < 10
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Mehr")
                }
            }

            Spacer(Modifier.height(16.dp))

            RatingChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Änderungs-Liste (neueste zuerst)
            Text(
                "Verlauf",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (chron.isEmpty()) {
                Text(
                    "Noch keine Änderungen eingetragen. Tippe + oder −, um den Wert anzupassen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                chron.reversed().forEach { entry ->
                    val old = entry.count
                    val new = entry.newRating ?: entry.count
                    val up = new > old
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${formatDateGerman(entry.loggedAt)} ${formatTimeGerman(entry.loggedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$old → $new",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                up -> MaterialTheme.colorScheme.primary
                                new < old -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showEdit) {
        HabitEditDialog(
            existing = habit,
            onDismiss = { showEdit = false },
            onSubmit = { form ->
                onEditHabit(habit.id, form)
                showEdit = false
            }
        )
    }
}

/**
 * Einfaches Liniendiagramm: X = Zeit, Y = Rating 0–10.
 * Reines Canvas (plattformneutral: Android/Web/Desktop), Y-Raster bei 0/5/10,
 * Linie + Punkte in primary, letzter Punkt hervorgehoben.
 */
@Composable
private fun RatingChart(
    points: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 30.dp, top = 8.dp, end = 8.dp, bottom = 20.dp)) {
            if (points.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height

            // Raster: 0 / 5 / 10
            listOf(0f, 5f, 10f).forEach { level ->
                val y = h - (level / 10f) * h
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                    pathEffect = if (level == 0f) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            // Wertebereich X normalisieren
            val minT = points.minOf { it.first }.toFloat()
            val maxT = points.maxOf { it.first }.toFloat()
            val spanT = (maxT - minT).coerceAtLeast(1f)

            fun px(t: Long): Float =
                if (points.size == 1) w / 2f else ((t.toFloat() - minT) / spanT) * w

            fun py(v: Int): Float = h - (v.coerceIn(0, 10) / 10f) * h

            // Linie
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, (t, v) ->
                    val x = px(t)
                    val y = py(v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            // Punkte
            points.forEachIndexed { i, (t, v) ->
                val x = px(t)
                val y = py(v)
                drawCircle(
                    color = if (i == points.lastIndex) lineColor else lineColor.copy(alpha = 0.75f),
                    radius = if (i == points.lastIndex) 10f else 7f,
                    center = Offset(x, y)
                )
                if (i == points.lastIndex) {
                    drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
                }
            }
        }

        // Y-Labels (10 / 5 / 0) links über dem Canvas
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 6.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("5", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("0", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }

        // X-Labels (erstes/letztes Datum) unten
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 30.dp, end = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val firstT = points.first().first
            val lastT = points.last().first
            if (points.size > 1 && firstT != lastT) {
                Text(formatDateGerman(firstT), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text(formatDateGerman(lastT), style = MaterialTheme.typography.labelSmall, color = labelColor)
            } else {
                Text(formatDateGerman(firstT), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}
