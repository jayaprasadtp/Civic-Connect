package com.example.civicconnect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.civicconnect.databinding.FragmentReportBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    private var imageUri: Uri? = null

    // Added for duplicate detection
    private var detectedLatitude: Double? = null
    private var detectedLongitude: Double? = null

    private var submissionDialog: AlertDialog? = null

    private var submissionStatusText: TextView? = null

    private var justAMinuteJob: Job? = null

    private val requestAllPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.any { it.value }
        if (!granted) {
            Toast.makeText(requireContext(), "Some permissions were denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) detectLocation()
        else Toast.makeText(requireContext(), "Location permission denied.", Toast.LENGTH_SHORT).show()
    }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                try {
                    val photoFile = File(requireContext().cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                    imageUri = Uri.fromFile(photoFile)
                    binding.btnTakePhoto.text = "Photo Captured ✓"
                    binding.btnUploadImage.text = "Upload Image"
                    Toast.makeText(requireContext(), "Photo captured successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Photo capture cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                imageUri = result.data!!.data
                binding.btnUploadImage.text = "Image Selected ✓"
                binding.btnTakePhoto.text = "Take Photo"
                Toast.makeText(requireContext(), "Image uploaded successfully!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        functions = FirebaseFunctions.getInstance("asia-south1")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupListeners()
        return binding.root
    }

    private fun setupListeners() {
        binding.btnAutoDetect.setOnClickListener { checkLocationPermissionAndDetect() }
        binding.btnTakePhoto.setOnClickListener { checkCameraPermissionAndCapture() }
        binding.btnUploadImage.setOnClickListener { checkStoragePermissionAndPick() }
        binding.btnSubmitIssue.setOnClickListener { submitIssue() }
    }

    // 📍 LOCATION
    private fun checkLocationPermissionAndDetect() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) detectLocation()
        else requestLocationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun detectLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                detectedLatitude = location.latitude
                detectedLongitude = location.longitude

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        val addressList = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        val address = addressList?.firstOrNull()?.getAddressLine(0) ?: "Unknown location"
                        withContext(Dispatchers.Main) {
                            if (!isAdded || _binding == null) return@withContext
                            binding.etLocation.setText(address)
                            Toast.makeText(requireContext(), "Location detected!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            if (!isAdded || _binding == null) return@withContext
                            Toast.makeText(requireContext(), "Failed to get address.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Could not fetch location. Try again.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Location detection failed.", Toast.LENGTH_SHORT).show()
        }
    }

    // 📸 CAMERA
    private fun checkCameraPermissionAndCapture() {
        val cameraPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        val storagePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            if (android.os.Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        )

        if (cameraPermission == PackageManager.PERMISSION_GRANTED && storagePermission == PackageManager.PERMISSION_GRANTED) {
            takePhotoLauncher.launch(null)
        } else {
            requestAllPermissions.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        Manifest.permission.READ_MEDIA_IMAGES
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }

    // 🖼️ GALLERY
    private fun checkStoragePermissionAndPick() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        val storagePermission = ContextCompat.checkSelfPermission(requireContext(), permission)
        if (storagePermission == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        } else {
            requestAllPermissions.launch(arrayOf(permission))
        }
    }

    private fun resolveCoordinates(locationText: String): Pair<Double, Double>? {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val address = geocoder.getFromLocationName(locationText, 1)?.firstOrNull()

            if (address != null) {
                address.latitude to address.longitude
            } else {
                val lat = detectedLatitude
                val lon = detectedLongitude
                if (lat != null && lon != null) lat to lon else null
            }
        } catch (_: Exception) {
            val lat = detectedLatitude
            val lon = detectedLongitude
            if (lat != null && lon != null) lat to lon else null
        }
    }

    private suspend fun checkDuplicateIssue(
        title: String,
        description: String,
        category: String,
        locationText: String
    ): Map<*, *>? {
        val coords = resolveCoordinates(locationText) ?: return null

        val data = hashMapOf(
            "title" to title,
            "description" to description,
            "category" to category,
            "latitude" to coords.first,
            "longitude" to coords.second
        )

        val result = functions.getHttpsCallable("checkDuplicateIssue").call(data).await()
        return result.data as? Map<*, *>
    }
    private suspend fun showSubmissionProgress(message: String) {
        withContext(Dispatchers.Main) {
            if (!isAdded || _binding == null) return@withContext

            binding.btnSubmitIssue.isEnabled = false

            if (submissionDialog?.isShowing == true) {
                submissionStatusText?.text = message
                return@withContext
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_submission_progress, null)
            submissionStatusText = dialogView.findViewById(R.id.tvSubmissionStatus)
            submissionStatusText?.text = message

            submissionDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            submissionDialog?.setCanceledOnTouchOutside(false)
            submissionDialog?.show()
            submissionDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private suspend fun updateSubmissionProgress(
        message: String,
        allowJustAMinute: Boolean = false
    ) {
        withContext(Dispatchers.Main) {
            if (!isAdded || _binding == null) return@withContext

            submissionStatusText?.text = message
            justAMinuteJob?.cancel()

            if (allowJustAMinute) {
                justAMinuteJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(1400)
                    if (isAdded && submissionDialog?.isShowing == true) {
                        submissionStatusText?.text = "Just a minute..."
                    }
                }
            }
        }
    }

    private suspend fun completeSubmissionProgress() {
        updateSubmissionProgress("Submitted!")
        delay(500)
        hideSubmissionProgress()
    }

    private suspend fun hideSubmissionProgress() {
        withContext(Dispatchers.Main) {
            justAMinuteJob?.cancel()
            justAMinuteJob = null

            if (_binding != null) {
                binding.btnSubmitIssue.isEnabled = true
            }

            submissionDialog?.dismiss()
            submissionDialog = null
            submissionStatusText = null
        }
    }

    // ✨ GEMINI PRIORITY CALL
    private suspend fun getPriorityFromGemini(title: String, description: String): Double {
        return try {
            val data = hashMapOf("title" to title, "description" to description)
            val result = functions.getHttpsCallable("getPriorityScore").call(data).await()
            val payload = result.data

            val score = when (payload) {
                is Map<*, *> -> {
                    val v = payload["priority"]
                    when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    }
                }
                is Number -> payload.toDouble()
                is String -> payload.toDoubleOrNull()
                else -> null
            } ?: 0.5

            score.coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            e.printStackTrace()
            0.5
        }
    }

    // ☁️ FIREBASE UPLOAD
    private fun submitIssue() {
        val title = binding.etIssueTitle.text.toString().trim()
        val desc = binding.etDescription.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val selectedChipId = binding.chipGroupCategory.checkedChipId
        val category = if (selectedChipId != View.NO_ID)
            binding.chipGroupCategory.findViewById<Chip>(selectedChipId).text.toString()
        else "Other"

        if (title.isEmpty() || desc.isEmpty() || location.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all required fields.", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val coords = resolveCoordinates(location)

                if (coords == null) {
                    withContext(Dispatchers.Main) {
                        if (!isAdded || _binding == null) return@withContext
                        Toast.makeText(
                            requireContext(),
                            "Could not resolve location coordinates. Please use Auto Detect or enter a clearer address.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val latitude = coords.first
                val longitude = coords.second

                var imageUrl: String? = null

                // Upload image if selected
                if (imageUri != null) {
                    val uploadUri = if (imageUri!!.scheme == "content") {
                        val tempFile = File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                        requireContext().contentResolver.openInputStream(imageUri!!)?.use { input ->
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                        }
                        Uri.fromFile(tempFile)
                    } else imageUri!!

                    val storageRef = storage.reference.child("issue_images/${UUID.randomUUID()}.jpg")
                    val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()

                    storageRef.putFile(uploadUri, metadata).await()
                    imageUrl = storageRef.downloadUrl.await().toString()
                }

                showSubmissionProgress("Checking duplicates...")

                val duplicateResult = checkDuplicateIssue(title, desc, category, location)

                val isDuplicate = duplicateResult?.get("isDuplicate") as? Boolean ?: false
                val duplicateOf = duplicateResult?.get("duplicateOf")?.toString()
                val duplicateTrackingNumber = duplicateResult?.get("duplicateTrackingNumber")?.toString()
                val remarkText = duplicateResult?.get("remark")?.toString()
                val embedding = duplicateResult?.get("embedding") as? List<*>

                val similarityScore = when (val value = duplicateResult?.get("similarityScore")) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                }

                val duplicateDistanceMeters = when (val value = duplicateResult?.get("duplicateDistanceMeters")) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                }

                updateSubmissionProgress("Analyzing priority...")

                val priority = if (isDuplicate) 0.0 else getPriorityFromGemini(title, desc)

                val now = System.currentTimeMillis()

                val remarks = if (isDuplicate && !remarkText.isNullOrBlank()) {
                    listOf(
                        hashMapOf<String, Any>(
                            "text" to remarkText,
                            "by" to "System",
                            "at" to now
                        )
                    )
                } else {
                    emptyList()
                }

                val issueId = firestore.collection("all_issues").document().id
                val issueData = hashMapOf(
                    "ownerUid" to uid,
                    "issueId" to issueId,
                    "title" to title,
                    "category" to category,
                    "description" to desc,
                    "location" to location,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "imageUrl" to imageUrl,
                    "status" to if (isDuplicate) "Rejected" else "Pending",
                    "timestamp" to now,
                    "trackingNumber" to "TRK-${UUID.randomUUID().toString().take(8).uppercase()}",
                    "priorityScore" to priority,
                    "duplicateOf" to if (isDuplicate) duplicateOf else null,
                    "duplicateTrackingNumber" to duplicateTrackingNumber,
                    "remarks" to remarks,
                    "remarksCount" to remarks.size,
                    "embedding" to (embedding ?: emptyList<Any>()),
                    "similarityScore" to similarityScore,
                    "duplicateDistanceMeters" to duplicateDistanceMeters
                )

                updateSubmissionProgress("Processing...", allowJustAMinute = true)
                firestore.collection("all_issues").document(issueId).set(issueData).await()

                completeSubmissionProgress()

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    Toast.makeText(requireContext(), "Issue submitted successfully!", Toast.LENGTH_LONG).show()

                    requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        .selectedItemId = R.id.nav_home

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commitAllowingStateLoss()
                }

            } catch (e: Exception) {
                hideSubmissionProgress()

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        justAMinuteJob?.cancel()
        submissionDialog?.dismiss()
        submissionDialog = null
        submissionStatusText = null
        _binding = null
        super.onDestroyView()
    }
}