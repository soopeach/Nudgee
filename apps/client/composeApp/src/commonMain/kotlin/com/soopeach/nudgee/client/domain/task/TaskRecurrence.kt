package com.soopeach.nudgee.client.domain.task

/** The intentionally small first release of Nudgee's recurring schedules. */
enum class TaskRecurrence(
    val rule: String?,
    val label: String,
) {
    DoesNotRepeat(null, "Does not repeat"),
    Daily("FREQ=DAILY", "Every day"),
    Weekdays("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "Every weekday"),
    Weekends("FREQ=WEEKLY;BYDAY=SA,SU", "Every weekend"),
    Weekly("FREQ=WEEKLY", "Every week");

    companion object {
        fun fromRule(rule: String?): TaskRecurrence = entries.firstOrNull { it.rule == rule } ?: DoesNotRepeat
    }
}

fun Task.recurrenceLabel(): String? = TaskRecurrence.fromRule(recurrenceRule)
    .takeUnless { it == TaskRecurrence.DoesNotRepeat }
    ?.label
