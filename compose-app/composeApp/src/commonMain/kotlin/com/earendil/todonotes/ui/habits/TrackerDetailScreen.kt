package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import com.earendil.todonotes.data.entity.HabitType
import com.earendil.todonotes.data.repo.HabitEngine
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.data.repo.formatTimeGerman
import com.earendil.todonotes.data.repo.nowMs
import com.earendil.todonotes.ui.BackHandler

/**
 * Detail-Ansicht eines Habits/Trackers:
 *
 * - SATISFACTION: Liniengrafik des Verlaufs (0–10) über die Zeit + +/− Buttons.
 * - HABIT: Balkendiagramm der abgeschlossenen Zeiträume (ein Datenpunkt = eine
 *   Periode, z.B. Woche). X-Achse zeigt den Zeitraum ("24.08 – 30.08"),
 *   Y-Achse die Anzahl der Logs darin. Gestrichelte Ziellinie = goalCount.
 *
 * Beide: Zahnrad (Ecke) → HabitEditDialog zum Bearbeiten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    habit: Habit,
    history: List<HabitHistoryEntry>,
    logs: List<HabitLog> = emptyList(),
    onRatingChange: (String, Int) -> Unit,
    onLogHabit: (String) -> Unit = {},
    onSetPeriodCount: (String, Long, Long, Int) -> Unit = { _, _, _, _ -> },
    onEditHabit: (String, HabitFormData) -> Unit,
    onBack: () -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }

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
            if (habit.type == HabitType.HABIT) {
                HabitPeriodSection(
                    habit = habit,
                    logs = logs,
                    history = history,
                    onLogHabit = onLogHabit,
                    onSetPeriodCount = onSetPeriodCount
                )
            } else {
                SatisfactionSection(
                    habit = habit,
                    history = history,
                    onRatingChange = onRatingChange
                )
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

// ---------------------------------------------------------------------------
// HABIT: Balkendiagramm pro abgeschlossener Periode
// ---------------------------------------------------------------------------

/** Eine Periode mit ihrer Log-Anzahl. endExclusive=null = laufende Periode. */
private data class PeriodStat(
    val start: Long,
    val endExclusive: Long?,
    val count: Int,
    val isCurrent: Boolean
)

@Composable
private fun HabitPeriodSection(
    habit: Habit,
    logs: List<HabitLog>,
    history: List<HabitHistoryEntry>,
    onLogHabit: (String) -> Unit,
    onSetPeriodCount: (String, Long, Long, Int) -> Unit
) {
    var editingPeriod by remember { mutableStateOf<PeriodStat?>(null) }
    val now = nowMs()
    val goal = habit.goalCount

    // Aktuelle Periode: Count aus Logs (reaktiv über logs-Param).
    val currentStart = HabitEngine.currentPeriodStart(habit, now)
    val currentCount = logs.count { it.timestamp >= currentStart }

    // Letzte ~12 Perioden (inkl. laufender), älteste zuerst.
    // Pro Periode: max(Log-Zahl, History-Eintrag) — letzterer deckt manuell
    // abgeschlossene Perioden ab, deren Logs gelöscht wurden.
    val stats = remember(habit.id, habit.startDate, habit.cadenceType, habit.interval,
        habit.resetWeekday, habit.resetAnchorDay, habit.resetAnchorMonth, logs, history) {
        val starts = HabitEngine.recentPeriods(habit, now, 12)
        val histByStart = history
            .filter { it.newRating == null }
            .associateBy { it.periodStart }
        starts.mapIndexed { i, s ->
            val next = starts.getOrNull(i + 1)
            val logCount = if (next != null)
                logs.count { it.timestamp >= s && it.timestamp < next }
            else
                logs.count { it.timestamp >= s }
            val hCount = histByStart[s]?.count ?: 0
            PeriodStat(
                start = s,
                endExclusive = next,
                count = maxOf(logCount, hCount),
                isCurrent = next == null
            )
        }
    }

    // Kopf: aktueller Stand + +1
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$currentCount",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            " / $goal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        FilledIconButton(onClick = { onLogHabit(habit.id) }) {
            Icon(Icons.Default.Add, contentDescription = "+1")
        }
    }
    Text(
        "Aktuelle Periode seit ${formatDateGerman(currentStart)} · ${cadenceLabel(habit)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))

    if (stats.size <= 1 && currentCount == 0) {
        // Gar keine Daten yet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Noch keine Daten. Drücke +1, sobald du „${habit.title}“ erledigt hast.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        PeriodBarChart(
            stats = stats,
            goal = goal,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Gestrichelte Linie = Ziel ($goal× pro ${periodUnitLabel(habit)})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    // Liste abgeschlossener Zeiträume (neueste zuerst)
    Text(
        "Zeiträume",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))
    val completed = stats.filter { !it.isCurrent }
    if (completed.isEmpty()) {
        Text(
            "Noch keine abgeschlossene Periode — der erste Datenpunkt erscheint, sobald eine Periode endet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        completed.reversed().forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    periodRangeLabel(p),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${p.count}×",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (p.count >= goal) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable { editingPeriod = p }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }

    editingPeriod?.let { p ->
        var editValue by remember(p) { mutableStateOf(p.count.toString()) }
        AlertDialog(
            onDismissRequest = { editingPeriod = null },
            confirmButton = {
                TextButton(onClick = {
                    val n = editValue.toIntOrNull() ?: 0
                    if (n >= 0 && n != p.count && p.endExclusive != null) {
                        onSetPeriodCount(habit.id, p.start, p.endExclusive, n)
                    }
                    editingPeriod = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingPeriod = null }) { Text("Abbrechen") }
            },
            title = { Text("Anzahl korrigieren") },
            text = {
                Column {
                    Text(
                        "Zeitraum: ${periodRangeLabel(p)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it.filter { c -> c.isDigit() } },
                        label = { Text("Anzahl") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        )
    }
}

/** "24.08. – 30.08." für eine abgeschlossene Periode. */
private fun periodRangeLabel(p: PeriodStat): String {
    val start = formatDateGerman(p.start)
    val endExclusive = p.endExclusive ?: return "seit $start"
    // End-Tag = (endExclusive - 1ms) → letzter Tag der Periode.
    val end = formatDateGerman(endExclusive - 1)
    // Bei Tages-Perioden (Länge ≤ 1 Tag) nur ein Datum zeigen.
    return if (endExclusive - p.start <= 24L * 60 * 60 * 1000) start else "$start – $end"
}

/** "Woche" / "Monat" / "Tag" / "3 Tage" für den Ziel-Hinweis. */
private fun periodUnitLabel(habit: Habit): String = when (habit.cadenceType) {
    CadenceType.DAY -> "Tag"
    CadenceType.WEEK -> "Woche"
    CadenceType.MONTH -> "Monat"
    CadenceType.YEAR -> "Jahr"
    CadenceType.NDAYS -> "${habit.interval} Tage"
}

/** "2x pro Woche" / "1x pro Tag" / "1x alle 3 Tage". */
private fun cadenceLabel(habit: Habit): String {
    val per = periodUnitLabel(habit)
    return if (habit.cadenceType == CadenceType.NDAYS) {
        "${habit.goalCount}x alle $per"
    } else {
        "${habit.goalCount}x pro $per"
    }
}

/**
 * Balkendiagramm: ein Balken pro Periode. Y = Anzahl, gestrichelte Ziellinie.
 * Laufende Periode etwas heller, leere Perioden als kleiner Stub (damit 0
 * als Datenpunkt sichtbar bleibt). X-Labels: erstes/letztes Perioden-Datum.
 */
@Composable
private fun PeriodBarChart(
    stats: List<PeriodStat>,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxY = maxOf(goal, stats.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 30.dp, top = 8.dp, end = 8.dp, bottom = 20.dp)
        ) {
            if (stats.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height

            // Grundlinie (0)
            drawLine(
                color = gridColor,
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1.5f
            )

            // Balken
            val n = stats.size.coerceAtLeast(1)
            val slot = w / n
            val barW = slot * 0.55f
            stats.forEachIndexed { i, p ->
                val x = slot * i + (slot - barW) / 2f
                val bh = if (p.count == 0) 4f else (p.count.toFloat() / maxY) * h
                drawRoundRect(
                    color = when {
                        p.count == 0 -> gridColor
                        p.isCurrent -> barColor.copy(alpha = 0.45f)
                        else -> barColor
                    },
                    topLeft = Offset(x, h - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }

            // Ziellinie (gestrichelt)
            if (goal > 0) {
                val gy = h - (goal.toFloat() / maxY) * h
                drawLine(
                    color = goalColor,
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
        }

        // Y-Labels (maxY / 0) links
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 6.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$maxY", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("0", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }

        // X-Labels (erstes/letztes Perioden-Datum) unten
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 30.dp, end = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(periodRangeLabel(stats.first()), style = MaterialTheme.typography.labelSmall, color = labelColor)
            if (stats.size > 1) {
                Text(periodRangeLabel(stats.last()), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SATISFACTION: Liniengrafik (wie bisher)
// ---------------------------------------------------------------------------

@Composable
private fun SatisfactionSection(
    habit: Habit,
    history: List<HabitHistoryEntry>,
    onRatingChange: (String, Int) -> Unit
) {
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
