package com.example.vitalrite_1.ui.user

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.vitalrite_1.data.Appointment
import com.example.vitalrite_1.data.Doctor
import com.example.vitalrite_1.data.DoctorAvailability
import com.example.vitalrite_1.ui.components.TopBar
import com.example.vitalrite_1.ui.components.UserBottomNav
import com.example.vitalrite_1.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    object Success : SearchState()
    object Failure : SearchState()
    object MultipleDoctors : SearchState()
}

@Composable
fun BookAppointmentScreen(
    navController: NavController,
    appointmentId: String? = null
) {
    val firestore = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var existingAppointment by remember { mutableStateOf<Appointment?>(null) }
    var doctorName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var doctorAvailability by remember { mutableStateOf<DoctorAvailability?>(null) }
    var doctorId by remember { mutableStateOf<String?>(null) }
    var patientName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var availableSlots by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(true) }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var matchingDoctors by remember { mutableStateOf(listOf<Doctor>()) }
    var specialties by remember { mutableStateOf(listOf<String>()) }

    // Permission launcher for notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            message = "Notification permission denied. Reminders won't be scheduled."
        }
    }

    // Fetch specialties on load
    LaunchedEffect(Unit) {
        firestore.collection("Specialties")
            .get()
            .addOnSuccessListener { snapshot ->
                specialties = snapshot.documents.mapNotNull { it.getString("name") }
            }
    }

    // Load existing appointment if provided
    LaunchedEffect(appointmentId) {
        if (appointmentId != null) {
            firestore.collection("Appointments")
                .document(appointmentId)
                .get()
                .addOnSuccessListener { doc ->
                    existingAppointment = doc.toObject(Appointment::class.java)?.copy(id = doc.id)
                    existingAppointment?.let { appt ->
                        doctorName = appt.doctorName
                        patientName = appt.patientName
                        age = appt.age
                        gender = appt.gender
                        date = appt.date
                        time = appt.time
                        searchDoctor(
                            doctorName = doctorName,
                            specialty = specialty,
                            firestore = firestore,
                            coroutineScope = coroutineScope,
                            onSearchStateChange = { searchState = it },
                            onMatchingDoctorsChange = { matchingDoctors = it },
                            onDoctorIdChange = { doctorId = it },
                            onDoctorNameChange = { doctorName = it },
                            onDoctorAvailabilityChange = { doctorAvailability = it },
                            onAvailableSlotsChange = { availableSlots = it },
                            updateSlots = {
                                updateAvailableSlots(
                                    doctorId = doctorId,
                                    date = date,
                                    firestore = firestore,
                                    doctorAvailability = doctorAvailability,
                                    onAvailableSlotsChange = { availableSlots = it },
                                    onError = { message = it }
                                )
                            }
                        )
                    }
                }
        }
    }

    val primaryColor = Color(0xFF6200EA)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF5F7FA), Color(0xFFE0E7FF))
    )

    Scaffold(
        topBar = {
            TopBar(
                navController,
                if (existingAppointment != null) "Reschedule Appointment" else "Book Appointment"
            )
        },
        bottomBar = { UserBottomNav(navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Specialty Dropdown
            DropdownMenuBox(
                label = "Specialty (Optional)",
                options = specialties,
                selectedOption = specialty,
                onSelected = {
                    specialty = it
                    searchDoctor(
                        doctorName = doctorName,
                        specialty = specialty,
                        firestore = firestore,
                        coroutineScope = coroutineScope,
                        onSearchStateChange = { searchState = it },
                        onMatchingDoctorsChange = { matchingDoctors = it },
                        onDoctorIdChange = { doctorId = it },
                        onDoctorNameChange = { doctorName = it },
                        onDoctorAvailabilityChange = { doctorAvailability = it },
                        onAvailableSlotsChange = { availableSlots = it },
                        updateSlots = {
                            updateAvailableSlots(
                                doctorId = doctorId,
                                date = date,
                                firestore = firestore,
                                doctorAvailability = doctorAvailability,
                                onAvailableSlotsChange = { availableSlots = it },
                                onError = { message = it }
                            )
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Doctor Name Search
            OutlinedTextField(
                value = doctorName,
                onValueChange = {
                    doctorName = it
                    showForm = true
                    message = ""
                    searchState = SearchState.Idle
                    doctorAvailability = null
                    doctorId = null
                    availableSlots = emptyList()
                },
                label = { Text("Doctor Name (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        searchDoctor(
                            doctorName = doctorName,
                            specialty = specialty,
                            firestore = firestore,
                            coroutineScope = coroutineScope,
                            onSearchStateChange = { searchState = it },
                            onMatchingDoctorsChange = { matchingDoctors = it },
                            onDoctorIdChange = { doctorId = it },
                            onDoctorNameChange = { doctorName = it },
                            onDoctorAvailabilityChange = { doctorAvailability = it },
                            onAvailableSlotsChange = { availableSlots = it },
                            updateSlots = {
                                updateAvailableSlots(
                                    doctorId = doctorId,
                                    date = date,
                                    firestore = firestore,
                                    doctorAvailability = doctorAvailability,
                                    onAvailableSlotsChange = { availableSlots = it },
                                    onError = { message = it }
                                )
                            }
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = primaryColor
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true,
                keyboardActions = KeyboardActions(onDone = {
                    searchDoctor(
                        doctorName = doctorName,
                        specialty = specialty,
                        firestore = firestore,
                        coroutineScope = coroutineScope,
                        onSearchStateChange = { searchState = it },
                        onMatchingDoctorsChange = { matchingDoctors = it },
                        onDoctorIdChange = { doctorId = it },
                        onDoctorNameChange = { doctorName = it },
                        onDoctorAvailabilityChange = { doctorAvailability = it },
                        onAvailableSlotsChange = { availableSlots = it },
                        updateSlots = {
                            updateAvailableSlots(
                                doctorId = doctorId,
                                date = date,
                                firestore = firestore,
                                doctorAvailability = doctorAvailability,
                                onAvailableSlotsChange = { availableSlots = it },
                                onError = { message = it }
                            )
                        }
                    )
                }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (searchState) {
                SearchState.Loading -> LoadingCard(primaryColor)
                SearchState.MultipleDoctors -> DoctorSelectionDialog(
                    doctors = matchingDoctors,
                    onDoctorSelected = { doctor ->
                        coroutineScope.launch {
                            handleSingleDoctor(
                                docId = doctor.uid,
                                trimmedName = doctor.name,
                                firestore = firestore,
                                onDoctorIdChange = { doctorId = it },
                                onDoctorNameChange = { doctorName = it },
                                onDoctorAvailabilityChange = { doctorAvailability = it },
                                onAvailableSlotsChange = { availableSlots = it },
                                onSearchStateChange = { searchState = it },
                                updateSlots = {
                                    updateAvailableSlots(
                                        doctorId = doctorId,
                                        date = date,
                                        firestore = firestore,
                                        doctorAvailability = doctorAvailability,
                                        onAvailableSlotsChange = { availableSlots = it },
                                        onError = { message = it }
                                    )
                                }
                            )
                            matchingDoctors = emptyList()
                        }
                    },
                    onDismiss = { searchState = SearchState.Idle }
                )
                SearchState.Success -> {
                    if (doctorAvailability != null && showForm) {
                        AppointmentForm(
                            doctorAvailability = doctorAvailability!!,
                            doctorId = doctorId!!,
                            patientName = patientName,
                            onPatientNameChange = { patientName = it },
                            age = age,
                            onAgeChange = { age = it },
                            gender = gender,
                            onGenderChange = { gender = it },
                            date = date,
                            onDateChange = {
                                date = it
                                if (doctorId != null && isValidDateFormat(it)) {
                                    coroutineScope.launch {
                                        updateAvailableSlots(
                                            doctorId = doctorId,
                                            date = it,
                                            firestore = firestore,
                                            doctorAvailability = doctorAvailability,
                                            onAvailableSlotsChange = { availableSlots = it },
                                            onError = { message = it }
                                        )
                                    }
                                } else if (!isValidDateFormat(it)) {
                                    message = "Invalid date format. Use yyyy-MM-dd."
                                }
                            },
                            time = time,
                            onTimeChange = { time = it },
                            availableSlots = availableSlots,
                            primaryColor = primaryColor,
                            existingAppointment = existingAppointment,
                            onBook = {
                                bookOrReschedule(
                                    existingAppointment = existingAppointment,
                                    userId = userId,
                                    doctorId = doctorId!!,
                                    patientName = patientName,
                                    doctorName = doctorName,
                                    date = date,
                                    time = time,
                                    age = age,
                                    gender = gender,
                                    firestore = firestore,
                                    navController = navController,
                                    context = context,
                                    permissionLauncher = permissionLauncher,
                                    onMessage = { message = it },
                                    onSuccess = {
                                        showForm = false
                                        patientName = ""
                                        age = ""
                                        date = ""
                                        time = ""
                                        searchState = SearchState.Idle
                                        doctorAvailability = null
                                        doctorId = null
                                        availableSlots = emptyList()
                                    }
                                )
                            },
                            onCancel = {
                                if (existingAppointment != null) {
                                    firestore.collection("Appointments")
                                        .document(existingAppointment!!.id)
                                        .delete()
                                        .addOnSuccessListener {
                                            message = "Appointment cancelled successfully!"
                                            navController.popBackStack()
                                        }
                                        .addOnFailureListener { e ->
                                            message = "Failed to cancel: ${e.message}"
                                        }
                                }
                            }
                        )
                    }
                }
                SearchState.Failure -> FailureCard()
                SearchState.Idle -> {}
            }

            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = if (message.contains("successfully")) Color.Green else Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DropdownMenuBox(
    label: String,
    options: List<String>,
    selectedOption: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val primaryColor = Color(0xFF6200EA)

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown",
                    tint = primaryColor,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(8.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = Color(0xFFBBBBBB),
                cursorColor = primaryColor,
                focusedLabelColor = primaryColor,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                disabledBorderColor = Color(0xFFEEEEEE)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .heightIn(max = 300.dp)
                .background(Color.White)
                .shadow(8.dp, RoundedCornerShape(8.dp))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            ),
                            color = Color.Black
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (option == selectedOption) Color(0xFFF5F7FA)
                            else Color.White
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun AppointmentForm(
    doctorAvailability: DoctorAvailability,
    doctorId: String,
    patientName: String,
    onPatientNameChange: (String) -> Unit,
    age: String,
    onAgeChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    time: String,
    onTimeChange: (String) -> Unit,
    availableSlots: List<String>,
    primaryColor: Color,
    existingAppointment: Appointment?,
    onBook: () -> Unit,
    onCancel: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    var doctorDetails by remember { mutableStateOf<Doctor?>(null) }

    LaunchedEffect(doctorId) {
        firestore.collection("Doctors")
            .document(doctorId)
            .get()
            .addOnSuccessListener { doc ->
                doctorDetails = doc.toObject(Doctor::class.java)
            }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Open: ${doctorAvailability.openTiming}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF666666)
                    )
                    Text(
                        "Close: ${doctorAvailability.closeTiming}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF666666)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            doctorDetails?.let { doctor ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Doctor Details",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Specialization: ${doctor.specialization}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                            Text(
                                "Experience: ${doctor.experience} years",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                            Text(
                                "Clinic: ${doctor.clinicName}, ${doctor.clinicAddress}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            item {
                OutlinedTextField(
                    value = patientName,
                    onValueChange = onPatientNameChange,
                    label = { Text("Patient Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        cursorColor = primaryColor,
                        focusedLabelColor = primaryColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                OutlinedTextField(
                    value = age,
                    onValueChange = onAgeChange,
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        cursorColor = primaryColor,
                        focusedLabelColor = primaryColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                DropdownMenuBox(
                    label = "Gender",
                    options = listOf("Male", "Female", "Other"),
                    selectedOption = gender,
                    onSelected = onGenderChange
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                OutlinedTextField(
                    value = date,
                    onValueChange = onDateChange,
                    label = { Text("Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFFBBBBBB),
                        cursorColor = primaryColor,
                        focusedLabelColor = primaryColor
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (doctorAvailability.holidays.contains(date)) {
                item {
                    Text(
                        "Doctor Unavailable",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                DropdownMenuBox(
                    label = "Time",
                    options = availableSlots,
                    selectedOption = time,
                    onSelected = onTimeChange
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onBook,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .shadow(4.dp, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (existingAppointment != null) "Reschedule" else "Book Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (existingAppointment != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFBBBBBB)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF666666)
                            )
                        ) {
                            Text(
                                "Cancel",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun updateAvailableSlots(
    doctorId: String?,
    date: String,
    firestore: FirebaseFirestore,
    doctorAvailability: DoctorAvailability?,
    onAvailableSlotsChange: (List<String>) -> Unit,
    onError: (String) -> Unit
) {
    if (doctorId == null || date.isEmpty() || doctorAvailability == null) {
        onAvailableSlotsChange(emptyList())
        return
    }

    if (!isValidDateFormat(date)) {
        onError("Invalid date format. Use yyyy-MM-dd")
        return
    }

    try {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val apptSnapshot = firestore.collection("Appointments")
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("date", date)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        val bookedSlots = apptSnapshot.map { it.toObject(Appointment::class.java).time }
        val allSlots = generateTimeSlots(
            doctorAvailability.openTiming,
            doctorAvailability.closeTiming,
            doctorAvailability.maxAppointmentsPerHour
        )
        val availableSlots = allSlots.filter { slot ->
            val hour = slot.split(":")[0].toInt()
            val bookedInHour = bookedSlots.count { it.startsWith(hour.toString().padStart(2, '0')) }
            !bookedSlots.contains(slot) && bookedInHour < doctorAvailability.maxAppointmentsPerHour
        }
        onAvailableSlotsChange(availableSlots)
    } catch (e: Exception) {
        Log.e("BookAppointmentScreen", "Failed to fetch slots: ${e.message}", e)
        onAvailableSlotsChange(emptyList())
        onError("Failed to load slots: ${e.message}")
    }
}

@Composable
fun LoadingCard(primaryColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = primaryColor)
        }
    }
}

@Composable
fun FailureCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            "No Record Found!",
            color = Color.Red,
            fontSize = 16.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun DoctorSelectionDialog(
    doctors: List<Doctor>,
    onDoctorSelected: (Doctor) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Multiple Doctors Found",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(doctors) { doctor ->
                        DoctorCard(doctor = doctor, onClick = { onDoctorSelected(doctor) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorCard(doctor: Doctor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(2.dp, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                doctor.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Text(
                "Specialization: ${doctor.specialization}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                "Experience: ${doctor.experience} years",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                "Clinic: ${doctor.clinicName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                "Location: ${doctor.clinicAddress}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

fun searchDoctor(
    doctorName: String,
    specialty: String,
    firestore: FirebaseFirestore,
    coroutineScope: CoroutineScope,
    onSearchStateChange: (SearchState) -> Unit,
    onMatchingDoctorsChange: (List<Doctor>) -> Unit,
    onDoctorIdChange: (String?) -> Unit,
    onDoctorNameChange: (String) -> Unit,
    onDoctorAvailabilityChange: (DoctorAvailability?) -> Unit,
    onAvailableSlotsChange: (List<String>) -> Unit,
    updateSlots: suspend () -> Unit
) {
    if (doctorName.isNotEmpty()) {
        val trimmedDoctorName = doctorName.trim()
        val lowercaseDoctorName = trimmedDoctorName.lowercase()
        onSearchStateChange(SearchState.Loading)

        coroutineScope.launch {
            try {
                val doctorSnapshot = firestore.collection("Doctors")
                    .whereEqualTo("nameLowercase", lowercaseDoctorName)
                    .get()
                    .await()

                if (!doctorSnapshot.isEmpty) {
                    if (doctorSnapshot.size() > 1) {
                        val doctors = doctorSnapshot.documents.map { doc ->
                            doc.toObject(Doctor::class.java)!!.copy(uid = doc.id)
                        }
                        onMatchingDoctorsChange(doctors)
                        onSearchStateChange(SearchState.MultipleDoctors)
                    } else {
                        handleSingleDoctor(
                            docId = doctorSnapshot.documents[0].id,
                            trimmedName = trimmedDoctorName,
                            firestore = firestore,
                            onDoctorIdChange = onDoctorIdChange,
                            onDoctorNameChange = onDoctorNameChange,
                            onDoctorAvailabilityChange = onDoctorAvailabilityChange,
                            onAvailableSlotsChange = onAvailableSlotsChange,
                            onSearchStateChange = onSearchStateChange,
                            updateSlots = updateSlots
                        )
                    }
                } else {
                    resetSearchState(
                        onDoctorAvailabilityChange = onDoctorAvailabilityChange,
                        onDoctorIdChange = onDoctorIdChange,
                        onAvailableSlotsChange = onAvailableSlotsChange,
                        onSearchStateChange = onSearchStateChange
                    )
                }
            } catch (e: Exception) {
                Log.e("BookAppointmentScreen", "Search failed: ${e.message}", e)
                resetSearchState(
                    onDoctorAvailabilityChange = onDoctorAvailabilityChange,
                    onDoctorIdChange = onDoctorIdChange,
                    onAvailableSlotsChange = onAvailableSlotsChange,
                    onSearchStateChange = onSearchStateChange
                )
            }
        }
    } else {
        onSearchStateChange(SearchState.Idle)
    }
}

suspend fun handleSingleDoctor(
    docId: String,
    trimmedName: String,
    firestore: FirebaseFirestore,
    onDoctorIdChange: (String?) -> Unit,
    onDoctorNameChange: (String) -> Unit,
    onDoctorAvailabilityChange: (DoctorAvailability?) -> Unit,
    onAvailableSlotsChange: (List<String>) -> Unit,
    onSearchStateChange: (SearchState) -> Unit,
    updateSlots: suspend () -> Unit
) {
    onDoctorIdChange(docId)
    val doctorNameFromDb = firestore.collection("Doctors")
        .document(docId)
        .get()
        .await()
        .getString("name") ?: trimmedName
    onDoctorNameChange(doctorNameFromDb)

    val availabilitySnapshot = firestore.collection("DoctorAvailability")
        .document(docId)
        .get()
        .await()

    val availability = availabilitySnapshot.toObject(DoctorAvailability::class.java)
    onDoctorAvailabilityChange(availability)
    if (availability != null) {
        updateSlots()
        onSearchStateChange(SearchState.Success)
    } else {
        resetSearchState(
            onDoctorAvailabilityChange = onDoctorAvailabilityChange,
            onDoctorIdChange = onDoctorIdChange,
            onAvailableSlotsChange = onAvailableSlotsChange,
            onSearchStateChange = onSearchStateChange
        )
    }
}

fun resetSearchState(
    onDoctorAvailabilityChange: (DoctorAvailability?) -> Unit,
    onDoctorIdChange: (String?) -> Unit,
    onAvailableSlotsChange: (List<String>) -> Unit,
    onSearchStateChange: (SearchState) -> Unit
) {
    onDoctorAvailabilityChange(null)
    onDoctorIdChange(null)
    onAvailableSlotsChange(emptyList())
    onSearchStateChange(SearchState.Failure)
}

suspend fun updateAvailableSlots(
    doctorId: String?,
    date: String,
    firestore: FirebaseFirestore,
    doctorAvailability: DoctorAvailability?,
    onExistingAppointmentsChange: (List<Appointment>) -> Unit,
    onAvailableSlotsChange: (List<String>) -> Unit,
    onError: (String) -> Unit = {}
) {
    if (doctorId == null || date.isEmpty() || doctorAvailability == null) {
        onError("Invalid input: doctorId, date, or availability missing")
        return
    }

    if (!isValidDateFormat(date)) {
        onError("Invalid date format. Use yyyy-MM-dd")
        return
    }

    try {
        Log.d("BookAppointmentScreen", "Fetching slots for doctorId: $doctorId, date: $date")
        val apptSnapshot = firestore.collection("Appointments")
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("date", date)
            .whereEqualTo("status", "Scheduled")
            .get()
            .await()

        val existingAppointments = apptSnapshot.map { it.toObject(Appointment::class.java) }
        onExistingAppointmentsChange(existingAppointments)

        val bookedSlots = existingAppointments.map { it.time }
        val allSlots = generateTimeSlots(
            open = doctorAvailability.openTiming ?: "09:00",
            close = doctorAvailability.closeTiming ?: "17:00",
            maxAppointmentsPerHour = doctorAvailability.maxAppointmentsPerHour.takeIf { it > 0 } ?: 1
        )
        val availableSlots = allSlots.filter { slot ->
            val hour = slot.split(":")[0].toInt()
            val bookedInHour = bookedSlots.count { it.startsWith(hour.toString().padStart(2, '0')) }
            !bookedSlots.contains(slot) && bookedInHour < (doctorAvailability.maxAppointmentsPerHour.takeIf { it > 0 } ?: 1)
        }
        Log.d("BookAppointmentScreen", "Available slots: $availableSlots")
        onAvailableSlotsChange(availableSlots)
    } catch (e: Exception) {
        Log.e("BookAppointmentScreen", "Failed to fetch slots: ${e.message}", e)
        onExistingAppointmentsChange(emptyList())
        onAvailableSlotsChange(emptyList())
        onError("Failed to load slots: ${e.message}")
    }
}

fun bookOrReschedule(
    existingAppointment: Appointment?,
    userId: String,
    doctorId: String,
    patientName: String,
    doctorName: String,
    date: String,
    time: String,
    age: String,
    gender: String,
    firestore: FirebaseFirestore,
    navController: NavController,
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onMessage: (String) -> Unit,
    onSuccess: () -> Unit
) {
    if (patientName.isEmpty() || age.isEmpty() || date.isEmpty() || time.isEmpty()) {
        onMessage("Please fill all fields")
        return
    }
    if (!isValidDateFormat(date)) {
        onMessage("Invalid date format. Use yyyy-MM-dd.")
        return
    }
    if (!isFutureDate(date)) {
        onMessage("Please select a future date.")
        return
    }

    val appointment = Appointment(
        id = existingAppointment?.id ?: "",
        userId = userId,
        doctorId = doctorId,
        patientName = patientName,
        doctorName = doctorName,
        date = date,
        time = time,
        age = age,
        gender = gender,
        status = "Scheduled"
    )

    val operation = if (existingAppointment != null) {
        firestore.collection("Appointments")
            .document(existingAppointment.id)
            .set(appointment)
    } else {
        firestore.collection("Appointments")
            .add(appointment)
    }

    operation
        .addOnSuccessListener { result ->
            val finalId = if (existingAppointment != null) {
                existingAppointment.id
            } else {
                (result as com.google.firebase.firestore.DocumentReference).id
            }
            val updatedAppointment = appointment.copy(id = finalId)

            if (!NotificationHelper.hasNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationHelper.scheduleAppointmentReminder(context, updatedAppointment)

            onMessage(
                if (existingAppointment != null) "Appointment rescheduled successfully!" else "Appointment booked successfully!"
            )
            onSuccess()
            navController.popBackStack()
        }
        .addOnFailureListener { e ->
            onMessage("Failed to ${if (existingAppointment != null) "reschedule" else "book"}: ${e.message}")
        }
}

fun generateTimeSlots(open: String, close: String, maxAppointmentsPerHour: Int): List<String> {
    val slots = mutableListOf<String>()
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val calendar = Calendar.getInstance()

    val openTime = sdf.parse(open) ?: return emptyList()
    val closeTime = sdf.parse(close) ?: return emptyList()

    calendar.time = openTime
    val openHour = calendar.get(Calendar.HOUR_OF_DAY)
    val openMinute = calendar.get(Calendar.MINUTE)

    calendar.time = closeTime
    val closeHour = calendar.get(Calendar.HOUR_OF_DAY)
    val closeMinute = calendar.get(Calendar.MINUTE)

    val interval = if (maxAppointmentsPerHour > 0) 60 / maxAppointmentsPerHour else 60
    var currentHour = openHour
    var currentMinute = openMinute

    while (currentHour < closeHour || (currentHour == closeHour && currentMinute < closeMinute)) {
        slots.add(String.format("%02d:%02d", currentHour, currentMinute))
        currentMinute += interval
        if (currentMinute >= 60) {
            currentMinute -= 60
            currentHour++
        }
    }

    return slots
}

fun isValidDateFormat(date: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(date)
        true
    } catch (e: Exception) {
        false
    }
}

fun isFutureDate(date: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDate = sdf.parse(date)
        val currentDate = Calendar.getInstance().time
        selectedDate?.after(currentDate) ?: false
    } catch (e: Exception) {
        false
    }
}