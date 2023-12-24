package com.rendox.routinetracker.core.database.completion_history

import com.rendox.routinetracker.core.model.Habit
import kotlinx.datetime.LocalDate

interface CompletionHistoryLocalDataSource {
    suspend fun getNumOfTimesCompletedInPeriod(
        habitId: Long,
        minDate: LocalDate?,
        maxDate: LocalDate?,
    ): Double

    suspend fun getRecordByDate(habitId: Long, date: LocalDate): Habit.CompletionRecord?
    suspend fun getLastCompletedRecord(habitId: Long): Habit.CompletionRecord?

    suspend fun insertCompletion(
        habitId: Long,
        completionRecord: Habit.CompletionRecord,
    )
}