package com.rendox.routinetracker.add_routine.tweak_routine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rendox.routinetracker.core.model.Schedule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Stable
class TweakRoutinePageState(
    startDate: LocalDate = LocalDate.now(),
    endDate: LocalDate? = null,
    overallNumOfDays: String = "",
    overallNumOfDaysIsValid: Boolean = true,
    sessionDuration: Duration? = null,
    sessionTime: LocalTime? = null,
    backlogEnabled: Boolean? = null,
    completingAheadEnabled: Boolean? = null,
    periodSeparationEnabled: Boolean? = null,
) {
    var startDate by mutableStateOf(startDate)
        private set

    var endDate by mutableStateOf(endDate)
        private set

    var overallNumOfDays by mutableStateOf(overallNumOfDays)
        private set

    var sessionDuration by mutableStateOf(sessionDuration)
        private set

    var sessionTime by mutableStateOf(sessionTime)
        private set

    var backlogEnabled by mutableStateOf(backlogEnabled)
        private set

    var completingAheadEnabled by mutableStateOf(completingAheadEnabled)
        private set

    var periodSeparationEnabled by mutableStateOf(periodSeparationEnabled)
        private set

    var overallNumOfDaysIsValid by mutableStateOf(overallNumOfDaysIsValid)
        private set

    var dialogType: TweakRoutinePageDialogType? by mutableStateOf(null)
        private set

    val containsError: Boolean
        get() = !overallNumOfDaysIsValid

    fun updateStartDate(startDate: LocalDate) {
        this.startDate = startDate
    }

    fun updateEndDate(endDate: LocalDate) {
        this.endDate = endDate
        this.overallNumOfDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toString()
    }

    fun updateOverallNumOfDays(numOfDays: String) {
        if (numOfDays.length <= 4) this.overallNumOfDays = numOfDays
        updateOverallNumOfDaysValidity()
        if (overallNumOfDaysIsValid) {
            this.endDate = startDate.plusDays((numOfDays.toInt() - 1).toLong())
        }
    }

    private fun updateOverallNumOfDaysValidity() {
        val numOfDays = try {
            this.overallNumOfDays.toInt()
        } catch (e: NumberFormatException) {
            overallNumOfDaysIsValid = false
            return
        }
        overallNumOfDaysIsValid = numOfDays > 1
    }

    fun switchEndDateEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            endDate = startDate.plusDays(29)
            overallNumOfDays = "30"
        } else {
            endDate = null
            overallNumOfDays = ""
        }
    }

    fun updateDialogType(dialogType: TweakRoutinePageDialogType?) {
        this.dialogType = dialogType
    }

    fun updateBacklogEnabled(enabled: Boolean) {
        backlogEnabled = enabled
    }

    fun updateCompletingAheadEnabled(enabled: Boolean) {
        completingAheadEnabled = enabled
    }

    fun updatePeriodSeparationEnabled(enabled: Boolean) {
        periodSeparationEnabled = enabled
    }

    fun updateChosenSchedule(chosenSchedule: Schedule) {
        backlogEnabled =
            if (chosenSchedule.supportsScheduleDeviation) chosenSchedule.backlogEnabled
            else null
        completingAheadEnabled =
            if (chosenSchedule.supportsScheduleDeviation) chosenSchedule.cancelDuenessIfDoneAhead
            else null
        periodSeparationEnabled =
            if (
                chosenSchedule is Schedule.PeriodicSchedule
                && chosenSchedule.supportsPeriodSeparation
            ) {
                chosenSchedule.periodSeparationEnabled
            } else null
    }

    companion object {
        val Saver: Saver<TweakRoutinePageState, *> = listSaver(
            save = { tweakRoutinePageState ->
                listOf(
                    tweakRoutinePageState.startDate,
                    tweakRoutinePageState.endDate,
                    tweakRoutinePageState.overallNumOfDays,
                    tweakRoutinePageState.overallNumOfDaysIsValid,
                    tweakRoutinePageState.sessionDuration,
                    tweakRoutinePageState.sessionTime,
                    tweakRoutinePageState.backlogEnabled,
                    tweakRoutinePageState.completingAheadEnabled,
                    tweakRoutinePageState.periodSeparationEnabled
                )
            },
            restore = { tweakRoutinePageStateValues ->
                TweakRoutinePageState(
                    startDate = tweakRoutinePageStateValues[0] as LocalDate,
                    endDate = tweakRoutinePageStateValues[1] as LocalDate?,
                    overallNumOfDays = tweakRoutinePageStateValues[2] as String,
                    overallNumOfDaysIsValid = tweakRoutinePageStateValues[3] as Boolean,
                    sessionDuration = tweakRoutinePageStateValues[4] as Duration?,
                    sessionTime = tweakRoutinePageStateValues[5] as LocalTime?,
                    backlogEnabled = tweakRoutinePageStateValues[6] as Boolean?,
                    completingAheadEnabled = tweakRoutinePageStateValues[7] as Boolean?,
                    periodSeparationEnabled = tweakRoutinePageStateValues[8] as Boolean?
                )
            }
        )
    }
}

@Composable
fun rememberTweakRoutinePageState() =
    rememberSaveable(saver = TweakRoutinePageState.Saver) {
        TweakRoutinePageState()
    }

enum class TweakRoutinePageDialogType {
    StartDatePicker,
    EndDatePicker,
}