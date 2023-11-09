package com.rendox.routinetracker.core.database.routine

import app.cash.sqldelight.TransactionWithoutReturn
import com.rendox.routinetracker.core.database.RoutineTrackerDatabase
import com.rendox.routinetracker.core.database.routine.model.RoutineType
import com.rendox.routinetracker.core.database.routine.model.ScheduleType
import com.rendox.routinetracker.core.database.routine.model.toAnnualScheduleByDueDates
import com.rendox.routinetracker.core.database.routine.model.toAnnualScheduleByNumOfDueDays
import com.rendox.routinetracker.core.database.routine.model.toCustomDateSchedule
import com.rendox.routinetracker.core.database.routine.model.toEveryDaySchedule
import com.rendox.routinetracker.core.database.routine.model.toMonthlyScheduleByDueDatesIndices
import com.rendox.routinetracker.core.database.routine.model.toMonthlyScheduleByNumOfDueDays
import com.rendox.routinetracker.core.database.routine.model.toPeriodicCustomSchedule
import com.rendox.routinetracker.core.database.routine.model.toWeeklyScheduleByDueDaysOfWeek
import com.rendox.routinetracker.core.database.routine.model.toWeeklyScheduleByNumOfDueDays
import com.rendox.routinetracker.core.database.routine.model.toYesNoRoutine
import com.rendox.routinetracker.core.database.schedule.ScheduleEntity
import com.rendox.routinetracker.core.database.toDayOfWeek
import com.rendox.routinetracker.core.database.toInt
import com.rendox.routinetracker.core.logic.time.WeekDayMonthRelated
import com.rendox.routinetracker.core.model.Routine
import com.rendox.routinetracker.core.model.Schedule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class RoutineLocalDataSourceImpl(
    private val db: RoutineTrackerDatabase,
    private val dispatcher: CoroutineDispatcher,
) : RoutineLocalDataSource {

    override suspend fun getRoutineById(routineId: Long): Routine {
        return withContext(dispatcher) {
            db.routineEntityQueries.transactionWithResult {
                val lastDateInHistory: LocalDate? = db.completionHistoryEntityQueries
                    .getLastHistoryEntryDate(routineId)
                    .executeAsOneOrNull()
                val schedule = getScheduleEntity(routineId).toExternalModel(
                    lastDateInHistory = lastDateInHistory,
                    dueDatesProvider = { getDueDates(routineId) },
                )
                getRoutineEntity(routineId).toExternalModel(schedule)
            }
        }
    }

    private fun getRoutineEntity(id: Long): RoutineEntity =
        db.routineEntityQueries.getRoutineById(id).executeAsOne()

    private fun getScheduleEntity(id: Long): ScheduleEntity =
        db.scheduleEntityQueries.getScheduleById(id).executeAsOne()

    private fun getDueDates(scheduleId: Long): List<Int> =
        db.dueDateEntityQueries.getDueDates(scheduleId).executeAsList()

    private fun ScheduleEntity.toExternalModel(
        lastDateInHistory: LocalDate?,
        dueDatesProvider: (Long) -> List<Int>
    ): Schedule {
        return when (type) {
            ScheduleType.EveryDaySchedule -> toEveryDaySchedule(lastDateInHistory)
            ScheduleType.WeeklyScheduleByDueDaysOfWeek ->
                toWeeklyScheduleByDueDaysOfWeek(dueDatesProvider(id), lastDateInHistory)

            ScheduleType.WeeklyScheduleByNumOfDueDays ->
                toWeeklyScheduleByNumOfDueDays(lastDateInHistory)

            ScheduleType.MonthlyScheduleByDueDatesIndices -> {
                val weekDaysMonthRelated = getWeekDayMonthRelatedDays(id)
                toMonthlyScheduleByDueDatesIndices(
                    dueDatesProvider(id), weekDaysMonthRelated, lastDateInHistory
                )
            }

            ScheduleType.MonthlyScheduleByNumOfDueDays ->
                toMonthlyScheduleByNumOfDueDays(lastDateInHistory)

            ScheduleType.PeriodicCustomSchedule -> toPeriodicCustomSchedule(lastDateInHistory)
            ScheduleType.CustomDateSchedule ->
                toCustomDateSchedule(dueDatesProvider(id), lastDateInHistory)

            ScheduleType.AnnualScheduleByDueDates ->
                toAnnualScheduleByDueDates(dueDatesProvider(id), lastDateInHistory)

            ScheduleType.AnnualScheduleByNumOfDueDays ->
                toAnnualScheduleByNumOfDueDays(lastDateInHistory)
        }
    }

    private fun RoutineEntity.toExternalModel(schedule: Schedule) = when (this.type) {
        RoutineType.YesNoRoutine -> this.toYesNoRoutine(
            schedule = schedule,
        )
    }

    private fun getWeekDayMonthRelatedDays(scheduleId: Long): List<WeekDayMonthRelated> =
        db.weekDayMonthRelatedEntityQueries
            .getWeekDayMonthRelatedDays(scheduleId)
            .executeAsList()
            .map { entity ->
                WeekDayMonthRelated(
                    dayOfWeek = entity.weekDayIndex.toDayOfWeek(),
                    weekDayNumberMonthRelated = entity.weekDayNumberMonthRelated,
                )
            }

    override suspend fun insertRoutine(routine: Routine) {
        return withContext(dispatcher) {
            db.routineEntityQueries.transaction {
                when (routine) {
                    is Routine.YesNoRoutine -> {
                        insertYesNoRoutine(routine)
                        insertSchedule(routine.schedule)
                    }
                }
            }
        }
    }

    @Suppress("UnusedReceiverParameter")
    private fun TransactionWithoutReturn.insertYesNoRoutine(routine: Routine.YesNoRoutine) {
        db.routineEntityQueries.insertRoutine(
            id = routine.id,
            type = RoutineType.YesNoRoutine,
            name = routine.name,
            description = routine.description,
            sessionDurationMinutes = routine.sessionDurationMinutes,
            progress = routine.progress,
            scheduleDeviation = routine.scheduleDeviation,
            defaultCompletionTimeHour = routine.defaultCompletionTime?.hour,
            defaultCompletionTimeMinute = routine.defaultCompletionTime?.minute,
        )
    }

    private fun insertSchedule(schedule: Schedule) {
        when (schedule) {
            is Schedule.EveryDaySchedule -> insertEveryDaySchedule(schedule)
            is Schedule.WeeklyScheduleByDueDaysOfWeek -> {
                insertWeeklyScheduleByDueDaysOfWeek(schedule)
                insertDueDates(schedule.dueDaysOfWeek.map { it.toInt() })
            }

            is Schedule.WeeklyScheduleByNumOfDueDays -> insertWeeklyScheduleByNumOfDueDays(schedule)

            is Schedule.MonthlyScheduleByDueDatesIndices -> {
                insertMonthlyScheduleByDueDatesIndices(schedule)
                val insertedScheduleId = db.scheduleEntityQueries.lastInsertRowId().executeAsOne()
                insertDueDates(schedule.dueDatesIndices)
                insertWeekDaysMonthRelated(insertedScheduleId, schedule.weekDaysMonthRelated)
            }

            is Schedule.MonthlyScheduleByNumOfDueDays -> insertMonthlyScheduleByNumOfDueDays(
                schedule
            )

            is Schedule.PeriodicCustomSchedule -> insertPeriodicCustomSchedule(schedule)

            is Schedule.CustomDateSchedule -> {
                insertCustomDateSchedule(schedule)
                insertDueDates(schedule.dueDates.map { it.toInt() })
            }

            is Schedule.AnnualScheduleByDueDates -> {
                insertAnnualScheduleByDueDates(schedule)
                insertDueDates(schedule.dueDates.map { it.toInt() })
            }

            is Schedule.AnnualScheduleByNumOfDueDays -> insertAnnualScheduleByNumOfDueDays(schedule)
        }
    }

    private fun insertEveryDaySchedule(schedule: Schedule.EveryDaySchedule) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.EveryDaySchedule,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = null,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = null,
            numOfDueDaysInByNumOfDueDaysSchedule = null,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = null,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertWeeklyScheduleByDueDaysOfWeek(schedule: Schedule.WeeklyScheduleByDueDaysOfWeek) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.WeeklyScheduleByDueDaysOfWeek,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = schedule.startDayOfWeek,
            startFromRoutineStartInMonthlyAndAnnualSchedule = null,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = null,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = null,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertWeeklyScheduleByNumOfDueDays(schedule: Schedule.WeeklyScheduleByNumOfDueDays) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.WeeklyScheduleByNumOfDueDays,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = schedule.startDayOfWeek,
            startFromRoutineStartInMonthlyAndAnnualSchedule = null,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = schedule.numOfDueDays,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = schedule.numOfDueDaysInFirstPeriod,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = schedule.numOfCompletedDaysInCurrentPeriod,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertMonthlyScheduleByDueDatesIndices(schedule: Schedule.MonthlyScheduleByDueDatesIndices) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.MonthlyScheduleByDueDatesIndices,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = schedule.startFromRoutineStart,
            includeLastDayOfMonthInMonthlySchedule = schedule.includeLastDayOfMonth,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = null,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = null,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertMonthlyScheduleByNumOfDueDays(schedule: Schedule.MonthlyScheduleByNumOfDueDays) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.MonthlyScheduleByNumOfDueDays,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = schedule.startFromRoutineStart,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = schedule.numOfDueDays,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = schedule.numOfDueDaysInFirstPeriod,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = schedule.numOfCompletedDaysInCurrentPeriod,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertWeekDaysMonthRelated(scheduleId: Long, values: List<WeekDayMonthRelated>) {
        for (weekDayMonthRelated in values) {
            db.weekDayMonthRelatedEntityQueries.insertWeekDayMonthRelatedEntry(
                id = null,
                scheduleId = scheduleId,
                weekDayIndex = weekDayMonthRelated.dayOfWeek.toInt(),
                weekDayNumberMonthRelated = weekDayMonthRelated.weekDayNumberMonthRelated,
            )
        }
    }

    private fun insertPeriodicCustomSchedule(schedule: Schedule.PeriodicCustomSchedule) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.PeriodicCustomSchedule,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = null,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = schedule.numOfDueDays,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = schedule.numOfCompletedDaysInCurrentPeriod,
            numOfDaysInPeriodicCustomSchedule = schedule.numOfDaysInPeriod,
        )
    }

    private fun insertCustomDateSchedule(schedule: Schedule.CustomDateSchedule) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.CustomDateSchedule,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = null,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = null,
            numOfDueDaysInByNumOfDueDaysSchedule = null,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = null,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertAnnualScheduleByDueDates(schedule: Schedule.AnnualScheduleByDueDates) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.AnnualScheduleByDueDates,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = schedule.startFromRoutineStart,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = null,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = null,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = null,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertAnnualScheduleByNumOfDueDays(schedule: Schedule.AnnualScheduleByNumOfDueDays) {
        db.scheduleEntityQueries.insertSchedule(
            id = null,
            type = ScheduleType.AnnualScheduleByNumOfDueDays,
            routineStartDate = schedule.routineStartDate,
            routineEndDate = schedule.routineEndDate,
            backlogEnabled = schedule.backlogEnabled,
            cancelDuenessIfDoneAhead = schedule.cancelDuenessIfDoneAhead,
            vacationStartDate = schedule.vacationStartDate,
            vacationEndDate = schedule.vacationEndDate,
            startDayOfWeekInWeeklySchedule = null,
            startFromRoutineStartInMonthlyAndAnnualSchedule = schedule.startFromRoutineStart,
            includeLastDayOfMonthInMonthlySchedule = null,
            periodicSeparationEnabledInPeriodicSchedule = schedule.periodSeparationEnabled,
            numOfDueDaysInByNumOfDueDaysSchedule = schedule.numOfDueDays,
            numOfDueDaysInFirstPeriodInByNumOfDueDaysSchedule = schedule.numOfDueDaysInFirstPeriod,
            numOfCompletedDaysInCurrentPeriodInByNumOfDueDaysSchedule = schedule.numOfCompletedDaysInCurrentPeriod,
            numOfDaysInPeriodicCustomSchedule = null,
        )
    }

    private fun insertDueDates(dueDates: List<Int>) {
        val lastInsertScheduleId = db.scheduleEntityQueries.lastInsertRowId().executeAsOne()
        for (dueDate in dueDates) {
            db.dueDateEntityQueries.insertDueDate(
                id = null,
                scheduleId = lastInsertScheduleId,
                dueDateNumber = dueDate,
                completionTimeHour = null,
                completionTimeMinute = null,
            )
        }
    }

    override suspend fun updateScheduleDeviation(newValue: Int, routineId: Long) {
        withContext(dispatcher) {
            db.routineEntityQueries.updateScheduleDeviation(newValue, routineId)
        }
    }
}