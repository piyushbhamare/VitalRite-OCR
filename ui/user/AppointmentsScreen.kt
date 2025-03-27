package com.example.vitalrite_1.ui.user

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.vitalrite_1.data.Appointment
import com.example.vitalrite_1.ui.components.TopBar
import com.example.vitalrite_1.ui.components.UserBottomNav
import com.example.vitalrite_1.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun AppointmentsScreen(navController: NavController) {
    var appointments by remember { mutableStateOf(listOf<Appointment>()) }
    var listenerRegistration by remember { mutableStateOf<ListenerRegistration?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("Appointments")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("AppointmentsScreen", "Listen failed.", e)
                    return@addSnapshotListener
                }
                appointments = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(Appointment::class.java)?.copy(id = document.id)
                }?.filter { it.isUpcoming() && it.status == "Scheduled" }
                    ?.sortedBy { "${it.date} ${it.time}" } ?: emptyList()
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            listenerRegistration?.remove()
        }
    }

    val primaryColor = Color(0xFF6200EA)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF5F7FA), Color(0xFFE0E7FF))
    )

    Scaffold(
        topBar = { TopBar(navController, "Appointments") },
        bottomBar = { UserBottomNav(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("bookAppointment") },
                containerColor = primaryColor,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Book Appointment",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    "Upcoming Appointments",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color.Black
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (appointments.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            "No Upcoming Appointments",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                    ) {
                        items(appointments) { appointment ->
                            AppointmentCard(
                                appointment = appointment,
                                onReschedule = { navController.navigate("bookAppointment/${appointment.id}") },
                                onCancel = {
                                    FirebaseFirestore.getInstance()
                                        .collection("Appointments")
                                        .document(appointment.id)
                                        .delete()
                                        .addOnSuccessListener {
                                            NotificationHelper.cancelAppointmentReminder(
                                                context,
                                                appointment
                                            )
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("AppointmentsScreen", "Error deleting appointment", e)
                                        }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(
    appointment: Appointment,
    onReschedule: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = Color(0xFF6200EA),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        appointment.doctorName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${appointment.date} at ${appointment.time}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Status: ${appointment.status}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = if (appointment.status == "Scheduled") Color(0xFF4CAF50) else Color.Red,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onReschedule,
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF6200EA)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF6200EA)
                    )
                ) {
                    Text(
                        "Reschedule",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .height(36.dp)
                        .shadow(2.dp, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}