package com.example.vitalrite_1.ui.user

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.vitalrite_1.data.Prescription
import com.example.vitalrite_1.data.User
import com.example.vitalrite_1.ui.components.TopBar
import com.example.vitalrite_1.ui.components.UserBottomNav
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EPreScreen(navController: NavController) {
    val firestore = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilters by remember { mutableStateOf(mutableSetOf<String>()) }
    var activePrescriptions by remember { mutableStateOf(listOf<Prescription>()) }
    var pastPrescriptions by remember { mutableStateOf(listOf<Prescription>()) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var isUploadMenuExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf<String?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var activePrescriptionListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var pastPrescriptionListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val context = navController.context
    val textRecognizer by remember { mutableStateOf(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            //takePictureLauncher.launch(null)
        } else {
            Log.e("EPreScreen", "Camera permission denied")
            // Optionally show a message to the user
            extractedText = "Camera permission denied. Please grant permission to capture photos."
            showTextDialog = true
        }
    }

    // Take picture launcher
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            isLoading = true
            saveImageLocallyAndProcess(
                bitmap,
                context,
                firestore,
                userId,
                textRecognizer,
                { prescription ->
                    isLoading = false
                    activePrescriptions = activePrescriptions + prescription
                },
                { text ->
                    isLoading = false
                    extractedText = text
                    showTextDialog = true
                }
            )
        }
    }

    // Pick image launcher
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isLoading = true
            saveImageUriLocallyAndProcess(
                it,
                context,
                firestore,
                userId,
                textRecognizer,
                { prescription ->
                    isLoading = false
                    activePrescriptions = activePrescriptions + prescription
                },
                { text ->
                    isLoading = false
                    extractedText = text
                    showTextDialog = true
                }
            )
        }
    }

    // Pick PDF launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isLoading = true
            savePdfLocallyAndProcess(it, context, firestore, userId) { prescription ->
                isLoading = false
                activePrescriptions = activePrescriptions + prescription
            }
        }
    }

    // Fetch prescriptions
    LaunchedEffect(searchQuery, selectedFilters) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()

        pastPrescriptionListener = firestore.collection("Prescriptions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("EPreScreen", "Listen failed for prescriptions.", e)
                    return@addSnapshotListener
                }

                val allPrescriptions = snapshot?.documents?.mapNotNull { doc ->
                    val prescription = doc.toObject(Prescription::class.java)?.copy(id = doc.id)
                    if (prescription != null) {
                        val expiryDate = try {
                            dateFormat.parse(prescription.expiryDate) ?: Date()
                        } catch (e: Exception) {
                            Log.e("EPreScreen", "Failed to parse expiryDate for ${prescription.id}: ${e.message}")
                            Date()
                        }

                        if (prescription.active && expiryDate.before(currentDate)) {
                            firestore.collection("Prescriptions").document(doc.id)
                                .update("active", false)
                                .addOnFailureListener { e ->
                                    Log.e("EPreScreen", "Failed to update active field for ${doc.id}: ${e.message}")
                                }
                        }
                        prescription
                    } else null
                }?.filterNotNull() ?: emptyList()

                val activeList = mutableListOf<Prescription>()
                val pastList = mutableListOf<Prescription>()
                allPrescriptions.forEach { prescription ->
                    val expiryDate = dateFormat.parse(prescription.expiryDate) ?: Date()
                    if (prescription.active && expiryDate.after(currentDate)) {
                        activeList.add(prescription)
                    } else {
                        pastList.add(prescription)
                    }
                }

                activePrescriptions = activeList.filter { prescription ->
                    if (searchQuery.isEmpty()) true
                    else {
                        when {
                            selectedFilters.contains("Diagnosis") -> prescription.mainCause.contains(searchQuery, ignoreCase = true)
                            selectedFilters.contains("Doctor Name") -> prescription.doctorName.contains(searchQuery, ignoreCase = true)
                            selectedFilters.contains("Prescription") -> prescription.name.contains(searchQuery, ignoreCase = true)
                            else -> true
                        }
                    }
                }

                pastPrescriptions = pastList.filter { prescription ->
                    if (searchQuery.isEmpty()) true
                    else {
                        when {
                            selectedFilters.contains("Diagnosis") -> prescription.mainCause.contains(searchQuery, ignoreCase = true)
                            selectedFilters.contains("Doctor Name") -> prescription.doctorName.contains(searchQuery, ignoreCase = true)
                            selectedFilters.contains("Prescription") -> prescription.name.contains(searchQuery, ignoreCase = true)
                            else -> true
                        }
                    }
                }

                firestore.collection("Users").document(userId).get()
                    .addOnSuccessListener { document ->
                        val user = document.toObject(User::class.java)
                        val activePrescriptionIds = user?.activePrescriptions ?: emptyList()
                        val updatedActivePrescriptionIds = activePrescriptionIds.filter { id ->
                            allPrescriptions.any { it.id == id && it.active && (dateFormat.parse(it.expiryDate) ?: Date()).after(currentDate) }
                        }
                        if (activePrescriptionIds != updatedActivePrescriptionIds) {
                            firestore.collection("Users").document(userId)
                                .update("activePrescriptions", updatedActivePrescriptionIds)
                                .addOnFailureListener { e ->
                                    Log.e("EPreScreen", "Failed to update activePrescriptions: ${e.message}")
                                }
                        }
                    }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            activePrescriptionListener?.remove()
            pastPrescriptionListener?.remove()
        }
    }

    val primaryColor = Color(0xFF6200EA)
    val backgroundGradient = Brush.verticalGradient(colors = listOf(Color(0xFFF5F7FA), Color(0xFFE0E7FF)))
    val filterCount = selectedFilters.size

    Scaffold(
        topBar = { TopBar(navController, "E-Prescriptions") },
        bottomBar = { UserBottomNav(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isUploadMenuExpanded = true },
                containerColor = primaryColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Prescription")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                ) {
                    Text(
                        "Your E-Prescriptions",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Icon(
                                Icons.Default.FilterAlt,
                                contentDescription = "Filter Icon",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp).clickable { isFilterExpanded = true }
                            )
                            if (filterCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(18.dp)
                                        .background(Color.Red, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        filterCount.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(1.dp)
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = isFilterExpanded,
                            onDismissRequest = { isFilterExpanded = false },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .align(Alignment.TopEnd)
                        ) {
                            listOf("Prescription", "Diagnosis", "Doctor Name").forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                option,
                                                color = Color.Black,
                                                fontSize = 16.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (selectedFilters.contains(option)) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.Green,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (selectedFilters.contains(option)) selectedFilters.remove(option) else selectedFilters.add(option)
                                        isFilterExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear All Filters",
                                        color = Color.Red,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                },
                                onClick = {
                                    selectedFilters.clear()
                                    searchQuery = ""
                                    isFilterExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = primaryColor
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                )
            }

            if (searchQuery.isNotEmpty() && activePrescriptions.isEmpty() && pastPrescriptions.isEmpty()) {
                Text(
                    "No record found",
                    color = Color.Red,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }

            Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(
                        "Active Prescriptions",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (activePrescriptions.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                "No Active Prescriptions",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            itemsIndexed(activePrescriptions) { index, prescription ->
                                CondensedPrescriptionItem(prescription, index + 1) {
                                    navController.navigate("prescriptionDetail/${prescription.id}")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Past Prescriptions",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pastPrescriptions.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                "No Past Prescriptions",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            itemsIndexed(pastPrescriptions) { index, prescription ->
                                CondensedPrescriptionItem(prescription, index + 1) {
                                    navController.navigate("prescriptionDetail/${prescription.id}")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                DropdownMenu(
                    expanded = isUploadMenuExpanded,
                    onDismissRequest = { isUploadMenuExpanded = false },
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .align(Alignment.BottomEnd)
                ) {
                    DropdownMenuItem(
                        text = { Text("Upload Image") },
                        onClick = {
                            isUploadMenuExpanded = false
                            pickImageLauncher.launch("image/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Capture Photo") },
                        onClick = {
                            isUploadMenuExpanded = false
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Upload PDF") },
                        onClick = {
                            isUploadMenuExpanded = false
                            pickPdfLauncher.launch("application/pdf")
                        }
                    )
                }
            }

            if (showTextDialog && extractedText != null) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = false },
                    title = { Text("Extracted Text") },
                    text = { Text(extractedText ?: "No text extracted") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val prescription = parsePrescriptionFromText(extractedText!!, userId)
                                isLoading = true
                                savePrescriptionToFirestore(firestore, prescription) { newPrescription ->
                                    isLoading = false
                                    activePrescriptions = activePrescriptions + newPrescription
                                    showTextDialog = false
                                }
                            }
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        Button(onClick = { showTextDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
        }
    }
}

// Updated Helper Functions with ML Kit and Better Error Handling
fun saveImageLocallyAndProcess(
    bitmap: Bitmap,
    context: Context,
    firestore: FirebaseFirestore,
    userId: String,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    onSuccess: (Prescription) -> Unit,
    onTextExtracted: (String) -> Unit
) {
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${UUID.randomUUID()}.jpg")
    try {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()
        FileOutputStream(file).use { it.write(data) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        processImageWithMLKit(uri, context, textRecognizer) { extractedText ->
            onTextExtracted(extractedText)
        }
    } catch (e: Exception) {
        Log.e("SaveImageLocally", "Failed to save or process image: ${e.message}", e)
        onTextExtracted("Failed to process image: ${e.message}")
    }
}

fun saveImageUriLocallyAndProcess(
    uri: Uri,
    context: Context,
    firestore: FirebaseFirestore,
    userId: String,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    onSuccess: (Prescription) -> Unit,
    onTextExtracted: (String) -> Unit
) {
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${UUID.randomUUID()}.jpg")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val localUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        processImageWithMLKit(localUri, context, textRecognizer) { extractedText ->
            onTextExtracted(extractedText)
        }
    } catch (e: Exception) {
        Log.e("SaveImageUriLocally", "Failed to save or process image: ${e.message}", e)
        onTextExtracted("Failed to process image: ${e.message}")
    }
}

fun processImageWithMLKit(
    uri: Uri,
    context: Context,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    onResult: (String) -> Unit
) {
    val image = InputImage.fromFilePath(context, uri)
    textRecognizer.process(image)
        .addOnSuccessListener { visionText ->
            val extractedText = visionText.text
            Log.d("MLKit", "Extracted text: $extractedText")
            onResult(extractedText)
        }
        .addOnFailureListener { e ->
            Log.e("MLKit", "Text recognition failed: ${e.message}", e)
            onResult("Text recognition failed: ${e.message}")
        }
}

fun savePdfLocallyAndProcess(
    uri: Uri,
    context: Context,
    firestore: FirebaseFirestore,
    userId: String,
    onSuccess: (Prescription) -> Unit
) {
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${UUID.randomUUID()}.pdf")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val prescription = Prescription(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = "Uploaded PDF Prescription",
            expiryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)),
            active = true,
            doctorName = "Unknown",
            mainCause = "Unknown",
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            medicines = emptyList()
        )
        savePrescriptionToFirestore(firestore, prescription, onSuccess)
    } catch (e: Exception) {
        Log.e("SavePdfLocally", "Failed to save PDF: ${e.message}", e)
    }
}

fun savePrescriptionToFirestore(
    firestore: FirebaseFirestore,
    prescription: Prescription,
    onSuccess: (Prescription) -> Unit
) {
    firestore.collection("Prescriptions")
        .document(prescription.id)
        .set(prescription)
        .addOnSuccessListener {
            firestore.collection("Users").document(prescription.userId)
                .update("activePrescriptions", FieldValue.arrayUnion(prescription.id))
                .addOnSuccessListener { onSuccess(prescription) }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Failed to update activePrescriptions: ${e.message}")
                }
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Failed to save prescription: ${e.message}")
        }
}

fun parsePrescriptionFromText(text: String, userId: String): Prescription {
    val lines = text.split("\n")
    val name = lines.find { it.contains("Prescription:") }?.substringAfter("Prescription:")?.trim() ?: "Unknown Prescription"
    val doctorName = lines.find { it.contains("Doctor:") }?.substringAfter("Doctor:")?.trim() ?: "Unknown Doctor"
    val mainCause = lines.find { it.contains("Cause:") }?.substringAfter("Cause:")?.trim() ?: "Unknown Cause"
    val expiryDate = lines.find { it.contains("Expiry:") }?.substringAfter("Expiry:")?.trim() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))

    return Prescription(
        id = UUID.randomUUID().toString(),
        userId = userId,
        name = name,
        expiryDate = expiryDate,
        active = true,
        doctorName = doctorName,
        mainCause = mainCause,
        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        medicines = emptyList()
    )
}

@Composable
fun CondensedPrescriptionItem(
    prescription: Prescription,
    prescriptionNumber: Int,
    onClick: () -> Unit
) {
    Log.d("CondensedPrescriptionItem", "Rendering prescription: ${prescription.name}")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Prescription - $prescriptionNumber",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Expiry Date: ${prescription.expiryDate}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Doctor: ${prescription.doctorName}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Cause: ${prescription.mainCause}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            )
        }
    }
}