package com.example.vitalrite_1.data

import android.Manifest
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.vitalrite_1.R
import com.example.vitalrite_1.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.BOOT_COMPLETED" -> {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Log.w(TAG, "User not logged in, cannot reschedule reminders after BOOT_COMPLETED")
                    return
                }

                val firestore = FirebaseFirestore.getInstance()

                firestore.collection("Users").document(userId).get()
                    .addOnSuccessListener { userDoc ->
                        val user = userDoc.toObject(User::class.java)
                        if (user == null) {
                            Log.w(TAG, "User data not found for userId: $userId")
                            return@addOnSuccessListener
                        }

                        // Reschedule medicine reminders
                        firestore.collection("Users").document(userId)
                            .collection("Reminders").get()
                            .addOnSuccessListener { reminderDocs ->
                                val reminders = reminderDocs.map { it.toObject(Reminder::class.java) }
                                reminders.forEach { reminder ->
                                    reminder.times.forEachIndexed { index, _ ->
                                        if (reminder.taken.getOrNull(index) != true) {
                                            try {
                                                Repository.scheduleReminder(context, reminder, user, index)
                                                Log.d(TAG, "Rescheduled reminder ${reminder.id} at index $index after BOOT_COMPLETED")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to reschedule reminder ${reminder.id}: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            }

                        // Reschedule appointment reminders
                        firestore.collection("Appointments")
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("status", "Scheduled")
                            .get()
                            .addOnSuccessListener { apptDocs ->
                                val appointments = apptDocs.map { it.toObject(Appointment::class.java).copy(id = it.id) }
                                appointments.forEach { appt ->
                                    NotificationHelper.scheduleAppointmentReminder(context, appt)
                                    Log.d(TAG, "Rescheduled appointment reminder ${appt.id} after BOOT_COMPLETED")
                                }
                            }
                    }
            }
            else -> {
                val reminderId = intent.getStringExtra("reminderId")
                val appointmentId = intent.getStringExtra("appointmentId")

                if (reminderId != null) {
                    // Handle medicine reminder
                    val medicineName = intent.getStringExtra("medicineName") ?: return
                    val time = intent.getStringExtra("time") ?: return
                    val timeIndex = intent.getIntExtra("timeIndex", -1)
                    if (timeIndex == -1) return

                    val channelId = "reminder_channel"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            enableLights(true)
                            enableVibration(true)
                            setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, null)
                        }
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.createNotificationChannel(channel)
                    }

                    val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                        action = "ACTION_TAKEN"
                        putExtra("reminderId", reminderId)
                        putExtra("timeIndex", timeIndex)
                    }
                    val takenPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (reminderId.hashCode() + timeIndex + 1),
                        takenIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                        action = "ACTION_SNOOZE"
                        putExtra("reminderId", reminderId)
                        putExtra("timeIndex", timeIndex)
                    }
                    val snoozePendingIntent = PendingIntent.getBroadcast(
                        context,
                        (reminderId.hashCode() + timeIndex + 2),
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notificationBuilder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Medicine Reminder")
                        .setContentText("Time to take $medicineName ($time)")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .setVibrate(longArrayOf(0, 500, 500, 500))
                        .addAction(android.R.drawable.ic_menu_save, "Taken", takenPendingIntent)
                        .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze", snoozePendingIntent)

                    notifyIfPermitted(context, reminderId.hashCode() + timeIndex, notificationBuilder)
                } else if (appointmentId != null) {
                    // Handle appointment reminder
                    val doctorName = intent.getStringExtra("doctorName") ?: return
                    val date = intent.getStringExtra("date") ?: return
                    val time = intent.getStringExtra("time") ?: return

                    val channelId = "appointment_channel"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Appointments",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            enableLights(true)
                            enableVibration(true)
                        }
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.createNotificationChannel(channel)
                    }

                    val notificationBuilder = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Upcoming Appointment")
                        .setContentText("Your appointment with $doctorName is tomorrow $date at $time")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)

                    notifyIfPermitted(context, appointmentId.hashCode(), notificationBuilder)
                }
            }
        }
    }

    private fun notifyIfPermitted(context: Context, id: Int, notificationBuilder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    with(NotificationManagerCompat.from(context)) {
                        notify(id, notificationBuilder.build())
                    }
                    Log.d(TAG, "Notification shown for ID $id")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to show notification: ${e.message}")
                }
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        } else {
            try {
                with(NotificationManagerCompat.from(context)) {
                    notify(id, notificationBuilder.build())
                }
                Log.d(TAG, "Notification shown for ID $id")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to show notification: ${e.message}")
            }
        }
    }
}