package com.example.vitalrite_1.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.vitalrite_1.data.Appointment
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {
    const val CHANNEL_ID = "appointment_reminders"
    private const val CHANNEL_NAME = "Appointment Reminders"
    private const val TAG = "NotificationHelper"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Below Android 12, no restriction applies
        }
    }

    fun scheduleAppointmentReminder(context: Context, appointment: Appointment) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppointmentReceiver::class.java).apply {
            putExtra("appointmentId", appointment.id)
            putExtra("doctorName", appointment.doctorName)
            putExtra("date", appointment.date)
            putExtra("time", appointment.time)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appointment.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val appointmentDateTime = sdf.parse("${appointment.date} ${appointment.time}") ?: return
        val calendar = Calendar.getInstance().apply {
            time = appointmentDateTime
            add(Calendar.HOUR_OF_DAY, -24) // Notify exactly 24 hours before
        }

        val triggerTime = calendar.timeInMillis
        if (triggerTime > System.currentTimeMillis()) {
            try {
                if (canScheduleExactAlarms(context)) {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Exact alarm scheduled for appointment ${appointment.id} at $triggerTime")
                } else {
                    // Fallback to inexact alarm if exact alarms are not allowed
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarms not allowed; using inexact alarm for appointment ${appointment.id}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule exact alarm for appointment ${appointment.id}: ${e.message}", e)
                // Fallback to inexact alarm
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            Log.d(TAG, "Reminder time $triggerTime is in the past for appointment ${appointment.id}; not scheduled")
        }
    }

    fun cancelAppointmentReminder(context: Context, appointment: Appointment) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppointmentReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appointment.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for appointment ${appointment.id}")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

class AppointmentReceiver : BroadcastReceiver() {
    private val TAG = "AppointmentReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createNotificationChannel(context)
        val appointmentId = intent.getStringExtra("appointmentId") ?: return
        val doctorName = intent.getStringExtra("doctorName") ?: return
        val date = intent.getStringExtra("date") ?: return
        val time = intent.getStringExtra("time") ?: return

        Log.d(TAG, "Received reminder for appointment $appointmentId with $doctorName on $date at $time")

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Appointment Reminder")
            .setContentText("Your appointment with $doctorName is in 24 hours ($date at $time).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        if (NotificationHelper.hasNotificationPermission(context)) {
            manager.notify(appointmentId.hashCode(), notification)
            Log.d(TAG, "Notification displayed for appointment $appointmentId")
        } else {
            Log.w(TAG, "No notification permission; skipping notification for appointment $appointmentId")
        }
    }
}