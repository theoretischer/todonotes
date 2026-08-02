package com.earendil.todonotes.data.repo

import org.junit.Test
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import java.util.Calendar
import java.util.TimeZone

class RecurrenceEngineDebug {

    @Test
    fun debugWeeklyMonToFri_startMonday_completeMonday() {
        // Setup: "jede 1 woche, MO-FR", Start = Montag diese Woche 14:00
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2025, Calendar.JANUARY, 6, 14, 0, 0) // Mo 6.1.2025 14:00
        cal.set(Calendar.MILLISECOND, 0)
        val fromDue = cal.timeInMillis

        // "now" = gleicher Montag, etwas später (14:05) - Nutzer hakt ab
        val nowCal = Calendar.getInstance(TimeZone.getDefault())
        nowCal.set(2025, Calendar.JANUARY, 6, 14, 5, 0)
        nowCal.set(Calendar.MILLISECOND, 0)
        val now = nowCal.timeInMillis

        println("fromDue=${DateTime(fromDue)} ($fromDue)")
        println("now=${DateTime(now)} ($now)")

        val rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        println("rrule=$rrule")
        println("nextOccurrence=${RecurrenceEngine.nextOccurrence(rrule, fromDue, now)}")
        println("expected: Dienstag 7.1.2025 14:00 (Option C: ab Fälligkeit weiter)")

        // Manuelle Iteration zum Vergleich
        val rule = RecurrenceRule(rrule)
        val start = DateTime(fromDue)
        println("start (raw)=${start}, swapTZ=${start.swapTimeZone(TimeZone.getDefault())}")
        val it: RecurrenceRuleIterator = rule.iterator(start)
        println("Iteration ab start:")
        var i = 0
        while (it.hasNext() && i < 8) {
            val inst = it.nextDateTime()
            val ts = inst.getTimestamp()
            println("  [$i] inst=$inst ts=$ts > now($now)? ${ts > now}")
            i++
        }
    }

    @Test
    fun debugDaily_startToday_completeToday() {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2025, Calendar.JANUARY, 6, 14, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val fromDue = cal.timeInMillis

        val nowCal = Calendar.getInstance(TimeZone.getDefault())
        nowCal.set(2025, Calendar.JANUARY, 6, 14, 5, 0)
        nowCal.set(Calendar.MILLISECOND, 0)
        val now = nowCal.timeInMillis

        val rrule = "FREQ=DAILY"
        println("DAILY: nextOccurrence=${RecurrenceEngine.nextOccurrence(rrule, fromDue, now)}")
        println("expected: Dienstag 7.1.2025 14:00")
    }

    @Test
    fun debugWeeklyMonToFri_startMonday_completeFridayBefore() {
        // Option C: Todo auf Montag, Nutzer hakt schon am FREITAG davor ab.
        // Erwartung: "Montag ist erledigt" → nächste Occurrence ab Montag = Dienstag.
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2025, Calendar.JANUARY, 6, 14, 0, 0) // Mo 6.1.2025 14:00
        cal.set(Calendar.MILLISECOND, 0)
        val fromDue = cal.timeInMillis

        val nowCal = Calendar.getInstance(TimeZone.getDefault())
        nowCal.set(2025, Calendar.JANUARY, 3, 10, 0, 0) // Fr 3.1.2025 10:00 (vor Fälligkeit!)
        nowCal.set(Calendar.MILLISECOND, 0)
        val now = nowCal.timeInMillis

        val rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        println("OptionC: fromDue(Mo)=${DateTime(fromDue)}, now(Fr davor)=${DateTime(now)}")
        println("nextOccurrence=${RecurrenceEngine.nextOccurrence(rrule, fromDue, now)}")
        println("expected: Dienstag 7.1.2025 14:00 (nicht Montag-nochmal, nicht Freitag-now)")
    }
}
