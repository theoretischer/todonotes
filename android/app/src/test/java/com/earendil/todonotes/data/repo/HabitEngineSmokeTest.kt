package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Stellt sicher, dass HabitEngine für jeden Cadence-Typ nicht crasht,
 *  auch bei Startdatum in Zukunft / Vergangenheit und null-Anchors. */
class HabitEngineSmokeTest {

    private fun cal(y: Int, m: Int, d: Int): Long {
        val c = Calendar.getInstance(TimeZone.getDefault())
        c.set(y, m - 1, d, 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun habit(type: CadenceType, start: Long, goal: Int = 2, interval: Int = 1) = Habit(
        id = "x", title = "T", cadenceType = type, interval = interval,
        resetWeekday = if (type == CadenceType.WEEK) Calendar.MONDAY else null,
        resetAnchorDay = if (type == CadenceType.MONTH || type == CadenceType.YEAR) 1 else null,
        resetAnchorMonth = if (type == CadenceType.YEAR) 1 else null,
        goalCount = goal, startDate = start, createdAt = start, updatedAt = start
    )

    @Test
    fun allCadenceTypes_startInFuture_noCrash() {
        val future = cal(2030, 1, 1)
        val now = cal(2026, 8, 2)
        CadenceType.entries.forEach { ct ->
            val h = habit(ct, future)
            val start = HabitEngine.currentPeriodStart(h, now)
            assertTrue("$ct: start sollte >= startDate sein", start >= future)
        }
    }

    @Test
    fun allCadenceTypes_startInPast_noCrash() {
        val past = cal(2020, 1, 1)
        val now = cal(2026, 8, 2)
        CadenceType.entries.forEach { ct ->
            val start = HabitEngine.currentPeriodStart(habit(ct, past), now)
            assertTrue("$ct: start sollte <= now sein", start <= now)
        }
    }

    @Test
    fun allCadenceTypes_nullAnchors_noCrash() {
        val now = cal(2026, 8, 2)
        // Absichtlich alle anchors null (so wie ein evtl. fehlerhaft gespeichertes Habit)
        CadenceType.entries.forEach { ct ->
            val h = Habit(
                id = "x", title = "T", cadenceType = ct, interval = 1,
                resetWeekday = null, resetAnchorDay = null, resetAnchorMonth = null,
                goalCount = 2, startDate = now, createdAt = now, updatedAt = now
            )
            // darf nicht werfen
            HabitEngine.currentPeriodStart(h, now)
        }
    }
}
