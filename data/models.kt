package com.example.vitalrite_1.data

import android.util.Log
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.*

data class User(
    val uid: String = "",
    val name: String = "",
    val age: String = "",
    val gender: String = "",
    val email: String = "",
    val breakfastTime: String = "",
    val lunchTime: String = "",
    val dinnerTime: String = "",
    val sleepTime: String = "",
    val bloodGroup: String = "",
    val medicalCondition: String = "",
    val operation: String = "",
    val allergy: String = "",
    val emergencyContact: String = "",
    val address: String = "",
    val activePrescriptions: List<String> = emptyList(),
    val lastReminderResetDate: String = ""
)

data class Doctor(
    val uid: String = "",
    val name: String = "",
    val nameLowercase: String = "",
    val age: String = "",
    val gender: String = "",
    val email: String = "",
    val degree: String = "",
    val specialization: String = "",
    val experience: String = "",
    val clinicName: String = "",
    val clinicAddress: String = "",
    val clinicPhone: String = "",
    val hospitalName: String = "",
    val hospitalAddress: String = "",
    val hospitalPhone: String = ""
)

@IgnoreExtraProperties
data class Appointment(
    val id: String = "",
    val userId: String = "",
    val doctorId: String = "",
    val patientName: String = "",
    val doctorName: String = "",
    val date: String = "",
    val time: String = "",
    val age: String = "",
    val gender: String = "",
    val status: String = "Scheduled",
    val lastNotificationTime: Long = 0L,
    val notificationIds: List<Int> = emptyList()
) {
    fun isUpcoming(): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val appointmentDateTime = dateFormat.parse("$date $time") ?: return false
        return appointmentDateTime.after(Date())
    }

    fun isToday(): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val appointmentDate = dateFormat.parse(date) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val appointmentCalendar = Calendar.getInstance().apply { time = appointmentDate }
        return today.get(Calendar.YEAR) == appointmentCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == appointmentCalendar.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) == appointmentCalendar.get(Calendar.DAY_OF_MONTH)
    }
}

data class Prescription(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val doctorName: String = "",
    val date: String = "",
    val mainCause: String = "",
    val medicines: List<Medicine> = emptyList(),
    val weight: String = "",
    val age: String = "",
    val expiryDate: String = "",
    val active: Boolean = false
)

data class DoctorAvailability(
    val doctorId: String = "",
    val openTiming: String = "",
    val closeTiming: String = "",
    val maxAppointmentsPerHour: Int = 0,
    val holidays: List<String> = emptyList()
)

data class Medicine(
    var name: String = "",
    var diagnosis: String = "",
    @PropertyName("time") var timeRaw: Any? = emptyList<String>(),
    var noOfDays: String = ""
)

data class Reminder(
    val id: String = "",
    val medicineName: String = "",
    val times: List<String> = emptyList(),
    val taken: List<Boolean> = emptyList(),
    val snoozeTimes: List<String?> = emptyList(),
    val date: String = ""
) {
    fun getNextReminderTime(user: User, timeIndex: Int): Long {
        if (timeIndex >= times.size) return Long.MAX_VALUE

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val snoozeTime = snoozeTimes.getOrNull(timeIndex)
        if (snoozeTime != null) {
            try {
                val snoozeDateTime = dateTimeFormat.parse("$date $snoozeTime")
                val snoozeMillis = snoozeDateTime?.time ?: Long.MAX_VALUE
                if (snoozeMillis < System.currentTimeMillis()) {
                    Log.d("Reminder", "Snooze time past: $snoozeDateTime")
                    return Long.MAX_VALUE
                }
                return snoozeMillis
            } catch (e: Exception) {
                Log.e("Reminder", "Snooze parse error: ${e.message}")
                return Long.MAX_VALUE
            }
        }

        val timeString = times.getOrNull(timeIndex) ?: return Long.MAX_VALUE
        val userTime = when (timeString) {
            "Before Breakfast" -> user.breakfastTime?.minus(15) ?: "08:00"
            "After Breakfast" -> user.breakfastTime ?: "08:15"
            "Before Lunch" -> user.lunchTime?.minus(15) ?: "13:00"
            "After Lunch" -> user.lunchTime ?: "13:15"
            "Before Dinner" -> user.dinnerTime?.minus(15) ?: "19:00"
            "After Dinner" -> user.dinnerTime ?: "19:15"
            "Before Sleep" -> user.sleepTime?.minus(15) ?: "22:00"
            else -> "08:00"
        }

        try {
            val triggerDateTime = dateTimeFormat.parse("$date $userTime")
            val triggerMillis = triggerDateTime?.time ?: Long.MAX_VALUE
            if (triggerMillis < System.currentTimeMillis()) {
                Log.d("Reminder", "Trigger time past: $triggerDateTime")
                return Long.MAX_VALUE
            }
            return triggerMillis
        } catch (e: Exception) {
            Log.e("Reminder", "Trigger parse error: ${e.message}")
            return Long.MAX_VALUE
        }
    }

    private fun String.minus(minutes: Int): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(this) ?: return this
        calendar.add(Calendar.MINUTE, -minutes)
        return timeFormat.format(calendar.time)
    }
}