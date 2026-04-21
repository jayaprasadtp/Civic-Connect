package com.example.civicconnect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.res.ColorStateList
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.civicconnect.databinding.FragmentReportBinding
import com.example.civicconnect.ml.PriorityTokenizer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.UUID
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.tensorflow.lite.Interpreter

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    private var priorityInterpreter: Interpreter? = null
    private var priorityInterpreterInitError: Throwable? = null
    private var priorityVocabulary: Map<String, Int>? = null
    private var imageUri: Uri? = null

    // Added for duplicate detection
    private var detectedLatitude: Double? = null
    private var detectedLongitude: Double? = null

    private var submissionDialog: AlertDialog? = null

    private var submissionStatusText: TextView? = null
    private var submissionHintText: TextView? = null

    private var justAMinuteJob: Job? = null
    private var isSubmitting = false

    private data class ReportDraft(
        val title: String,
        val description: String,
        val location: String,
        val category: String
    )

    private enum class SelectedImageSource {
        CAMERA,
        GALLERY
    }

    companion object {
        private const val TAG = "ReportFragment"
        private const val DEFAULT_SUBMISSION_HINT =
            "We’ll score the report, check for duplicates, and save it securely."
        private const val SLOW_SUBMISSION_HINT =
            "This is taking a little longer than usual, but your report is still on the way."
        private const val CAMERA_DEFAULT_LABEL = "Take Photo"
        private const val CAMERA_SELECTED_LABEL = "Photo Ready"
        private const val GALLERY_DEFAULT_LABEL = "Upload Image"
        private const val GALLERY_SELECTED_LABEL = "Image Ready"
    }

    private val requestAllPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.any { it.value }
        if (!granted) {
            showFeedback("Some permissions were denied.", true)
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) detectLocation()
        else showFeedback("Location permission denied.", true)
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
                    updateImageActionButtons(SelectedImageSource.CAMERA)
                    showFeedback("Photo captured successfully.")
                } catch (e: Exception) {
                    showFeedback("Failed to save photo: ${e.message}", true)
                }
            } else {
                showFeedback("Photo capture cancelled.", true)
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                imageUri = result.data!!.data
                updateImageActionButtons(SelectedImageSource.GALLERY)
                showFeedback("Image selected successfully.")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)

        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        functions = FirebaseFunctions.getInstance("asia-south1")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        priorityVocabulary = initialisePriorityVocabulary()
        priorityInterpreter = initialisePriorityInterpreter()

        updateImageActionButtons(null)
        setupListeners()
        return binding.root
    }

    private fun setupListeners() {
        binding.btnAutoDetect.setOnClickListener { checkLocationPermissionAndDetect() }
        binding.btnTakePhoto.setOnClickListener { checkCameraPermissionAndCapture() }
        binding.btnUploadImage.setOnClickListener { checkStoragePermissionAndPick() }
        binding.btnSubmitIssue.setOnClickListener { submitIssue() }
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        if (!isAdded || _binding == null) return
        showAppToast(message, isError)
    }

    private fun updateImageActionButtons(selectedSource: SelectedImageSource?) {
        if (!isAdded || _binding == null) return

        val selectedBackground = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.surface_nav_indicator)
        )
        val defaultBackground = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), android.R.color.transparent)
        )
        val selectedStroke = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.brand_primary)
        )
        val defaultStroke = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.stroke_soft)
        )
        val selectedContentColor = ContextCompat.getColor(requireContext(), R.color.brand_primary_dark)
        val defaultContentColor = ContextCompat.getColor(requireContext(), R.color.brand_primary)

        fun styleButton(
            button: MaterialButton,
            isSelected: Boolean,
            defaultLabel: String,
            selectedLabel: String
        ) {
            button.text = if (isSelected) selectedLabel else defaultLabel
            button.backgroundTintList = if (isSelected) selectedBackground else defaultBackground
            button.strokeColor = if (isSelected) selectedStroke else defaultStroke
            button.setTextColor(if (isSelected) selectedContentColor else defaultContentColor)
            button.iconTint = ColorStateList.valueOf(
                if (isSelected) selectedContentColor else defaultContentColor
            )
        }

        styleButton(
            button = binding.btnTakePhoto,
            isSelected = selectedSource == SelectedImageSource.CAMERA,
            defaultLabel = CAMERA_DEFAULT_LABEL,
            selectedLabel = CAMERA_SELECTED_LABEL
        )
        styleButton(
            button = binding.btnUploadImage,
            isSelected = selectedSource == SelectedImageSource.GALLERY,
            defaultLabel = GALLERY_DEFAULT_LABEL,
            selectedLabel = GALLERY_SELECTED_LABEL
        )
    }

    private fun updateSubmitButtonState(isLoading: Boolean) {
        if (!isAdded || _binding == null) return

        isSubmitting = isLoading
        binding.btnSubmitIssue.isEnabled = !isLoading
        binding.btnSubmitIssue.text = if (isLoading) "Submitting..." else "Submit Issue"
    }

    private fun clearValidationErrors() {
        if (!isAdded || _binding == null) return

        binding.tilIssueTitle.error = null
        binding.tilDescription.error = null
        binding.tilLocation.error = null
    }

    private fun validateReportDraft(): ReportDraft? {
        clearValidationErrors()

        val title = binding.etIssueTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val selectedChipId = binding.chipGroupCategory.checkedChipId

        var firstInvalidView: View? = null

        if (title.isEmpty()) {
            binding.tilIssueTitle.error = "Add a short title."
            firstInvalidView = binding.etIssueTitle
        }

        if (description.isEmpty()) {
            binding.tilDescription.error = "Add a few details so the team can act on it."
            if (firstInvalidView == null) firstInvalidView = binding.etDescription
        }

        if (location.isEmpty()) {
            binding.tilLocation.error = "Enter the location or use auto-detect."
            if (firstInvalidView == null) firstInvalidView = binding.etLocation
        }

        if (selectedChipId == View.NO_ID) {
            showFeedback("Choose a category for this report.", true)
            if (firstInvalidView == null) firstInvalidView = binding.chipGroupCategory
        }

        if (firstInvalidView != null) {
            firstInvalidView.requestFocus()
            return null
        }

        val category = binding.chipGroupCategory
            .findViewById<Chip>(selectedChipId)
            .text
            .toString()

        return ReportDraft(
            title = title,
            description = description,
            location = location,
            category = category
        )
    }

    private fun initialisePriorityInterpreter(): Interpreter? {
        return runCatching {
            Interpreter(
                loadModelFile(),
                Interpreter.Options().apply {
                    setNumThreads(2)
                }
            )
        }.onFailure { error ->
            priorityInterpreterInitError = error
            Log.e(TAG, "Failed to initialise priority model.", error)
        }.getOrNull()
    }

    private fun initialisePriorityVocabulary(): Map<String, Int>? {
        return runCatching {
            PriorityTokenizer.loadVocabulary(requireContext())
        }.onFailure { error ->
            Log.e(TAG, "Failed to load priority vocabulary.", error)
        }.getOrNull()
    }

    private fun loadModelFile(): ByteBuffer {
        return try {
            requireContext().assets.openFd(PriorityTokenizer.MODEL_ASSET_PATH).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).channel.use { fileChannel ->
                    fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Falling back to buffered model loading for ${PriorityTokenizer.MODEL_ASSET_PATH}", error)

            requireContext().assets.open(PriorityTokenizer.MODEL_ASSET_PATH).use { input ->
                val bytes = input.readBytes()
                ByteBuffer
                    .allocateDirect(bytes.size)
                    .order(ByteOrder.nativeOrder())
                    .put(bytes)
                    .apply { rewind() }
            }
        }
    }

    private fun predictPriorityScore(
        title: String,
        description: String,
        category: String
    ): Double {
        val interpreter = priorityInterpreter
            ?: error(
                priorityInterpreterInitError?.message
                    ?: "Priority model is not available on this device."
            )
        val vocabulary = priorityVocabulary
            ?: error("Priority vocabulary is not available on this device.")

        val input = arrayOf(
            PriorityTokenizer.encode(
                title = title,
                description = description,
                category = category,
                vocabulary = vocabulary
            )
        )
        val output = Array(1) { FloatArray(1) }

        interpreter.run(input, output)

        return output[0][0].toDouble().coerceIn(0.0, 1.0)
    }

    private suspend fun ensureFreshAuthSession() {
        val user = auth.currentUser ?: error("Your session has expired. Please log in again.")
        val token = user.getIdToken(true).await().token

        if (token.isNullOrBlank()) {
            error("Could not verify your account. Please log in again.")
        }
    }

    private fun redirectToLogin(message: String) {
        if (!isAdded) return

        auth.signOut()
        requireContext()
            .getSharedPreferences("CivicPrefs", Activity.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        showFeedback(message, true)

        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
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
                            showFeedback("Location detected.")
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            if (!isAdded || _binding == null) return@withContext
                            showFeedback("Failed to get address.", true)
                        }
                    }
                }
            } else {
                showFeedback("Could not fetch location. Try again.", true)
            }
        }.addOnFailureListener {
            showFeedback("Location detection failed.", true)
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

    private suspend fun showSubmissionProgress(
        message: String,
        hint: String = DEFAULT_SUBMISSION_HINT
    ) {
        withContext(Dispatchers.Main) {
            if (!isAdded || _binding == null) return@withContext

            updateSubmitButtonState(true)

            if (submissionDialog?.isShowing == true) {
                submissionStatusText?.text = message
                submissionHintText?.text = hint
                return@withContext
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_submission_progress, null)
            submissionStatusText = dialogView.findViewById(R.id.tvSubmissionStatus)
            submissionHintText = dialogView.findViewById(R.id.tvSubmissionHint)
            submissionStatusText?.text = message
            submissionHintText?.text = hint

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
        hint: String? = null,
        allowJustAMinute: Boolean = false
    ) {
        withContext(Dispatchers.Main) {
            if (!isAdded || _binding == null) return@withContext

            submissionStatusText?.text = message
            if (hint != null) {
                submissionHintText?.text = hint
            }
            justAMinuteJob?.cancel()

            if (allowJustAMinute) {
                justAMinuteJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(1400)
                    if (isAdded && submissionDialog?.isShowing == true) {
                        submissionHintText?.text = SLOW_SUBMISSION_HINT
                    }
                }
            }
        }
    }

    private suspend fun completeSubmissionProgress() {
        updateSubmissionProgress(
            "Report saved.",
            "Opening your issues so you can track it right away."
        )
        delay(550)
        hideSubmissionProgress()
    }

    private suspend fun hideSubmissionProgress() {
        withContext(Dispatchers.Main) {
            justAMinuteJob?.cancel()
            justAMinuteJob = null
            updateSubmitButtonState(false)

            submissionDialog?.dismiss()
            submissionDialog = null
            submissionStatusText = null
            submissionHintText = null
        }
    }

    private fun openMyIssues(trackingNumber: String?) {
        if (!isAdded) return

        val fragment = MyIssuesFragment().apply {
            arguments = Bundle().apply {
                if (!trackingNumber.isNullOrBlank()) {
                    putString("highlight_tracking", trackingNumber)
                }
            }
        }

        requireActivity()
            .findViewById<BottomNavigationView>(R.id.bottom_navigation)
            .menu
            .findItem(R.id.nav_my_issues)
            ?.isChecked = true

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }

    private fun formatSubmissionError(error: Exception): String {
        val functionsException = error as? FirebaseFunctionsException

        return when (functionsException?.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsException.message?.takeIf { it.isNotBlank() }
                    ?: "Some report details look incomplete. Please review them and try again."
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                "The submission took too long. Please check your connection and try again."
            FirebaseFunctionsException.Code.UNAVAILABLE ->
                "We couldn't reach the server just now. Please try again in a moment."
            FirebaseFunctionsException.Code.INTERNAL ->
                "Something went wrong while saving the report. Please try again."
            else ->
                error.message?.takeIf { it.isNotBlank() }
                    ?: "We couldn't submit your report right now."
        }
    }

    private fun submitIssue() {
        if (isSubmitting) {
            return
        }

        val draft = validateReportDraft() ?: return

        auth.currentUser?.uid ?: run {
            redirectToLogin("Your session has expired. Please log in again.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showSubmissionProgress(
                    "Getting everything ready...",
                    "We’re validating your report before it is sent."
                )
                updateSubmissionProgress(
                    "Checking your account...",
                    "Making sure this report is securely linked to you."
                )
                ensureFreshAuthSession()

                updateSubmissionProgress(
                    "Resolving the location...",
                    "We’re translating the address into map coordinates."
                )

                val coords = resolveCoordinates(draft.location)

                if (coords == null) {
                    hideSubmissionProgress()
                    withContext(Dispatchers.Main) {
                        if (!isAdded || _binding == null) return@withContext
                        showFeedback(
                            "Could not resolve location coordinates. Please use Auto Detect or enter a clearer address.",
                            true
                        )
                    }
                    return@launch
                }

                val latitude = coords.first
                val longitude = coords.second

                updateSubmissionProgress(
                    "Estimating priority...",
                    "The on-device model is scoring urgency from the report details."
                )

                val priority = predictPriorityScore(draft.title, draft.description, draft.category)
                val priorityBand = PriorityBands.fromScore(priority)

                var imageUrl: String? = null

                if (imageUri != null) {
                    updateSubmissionProgress(
                        "Uploading your photo...",
                        "Large photos can take a few extra seconds on slower networks.",
                        allowJustAMinute = true
                    )

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

                updateSubmissionProgress(
                    "Saving your report...",
                    "We’re checking for duplicates and generating a tracking number.",
                    allowJustAMinute = true
                )

                val payload = hashMapOf(
                    "title" to draft.title,
                    "category" to draft.category,
                    "description" to draft.description,
                    "location" to draft.location,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "imageUrl" to imageUrl,
                    "priorityScore" to priority
                )

                val result = functions
                    .getHttpsCallable("submitIssuePipeline")
                    .call(payload)
                    .await()

                val response = result.data as? Map<*, *>
                val returnedStatus = response?.get("status")?.toString()
                val returnedTrackingNumber = response?.get("trackingNumber")?.toString()
                val duplicateTrackingNumber = response?.get("duplicateTrackingNumber")?.toString()
                val remark = response?.get("remark")?.toString()

                completeSubmissionProgress()

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    val message = when {
                        returnedStatus.equals("Rejected", true) && !duplicateTrackingNumber.isNullOrBlank() ->
                            "A similar report already exists. Your submission was linked to $duplicateTrackingNumber."
                        returnedStatus.equals("Rejected", true) && !remark.isNullOrBlank() ->
                            remark
                        !returnedTrackingNumber.isNullOrBlank() ->
                            "Report submitted as ${priorityBand.label.lowercase()} priority. Tracking number: $returnedTrackingNumber."
                        else ->
                            "Report submitted successfully."
                    }

                    showFeedback(message)
                    openMyIssues(returnedTrackingNumber)
                }

            } catch (e: Exception) {
                hideSubmissionProgress()

                withContext(Dispatchers.Main) {
                    if (!isAdded || _binding == null) return@withContext

                    if (e.message?.contains("log in again", ignoreCase = true) == true) {
                        redirectToLogin(e.message ?: "Please log in again.")
                        return@withContext
                    }

                    val functionsException = e as? FirebaseFunctionsException

                    Log.e(TAG, "Report submission failed.", e)

                    if (functionsException?.code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                        val authMessage = functionsException.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "Your session could not be verified. Please log in again."

                        redirectToLogin(authMessage)
                        return@withContext
                    }

                    showFeedback(formatSubmissionError(e), true)
                }
            }
        }
    }

    override fun onDestroyView() {
        justAMinuteJob?.cancel()
        submissionDialog?.dismiss()
        submissionDialog = null
        submissionStatusText = null
        submissionHintText = null
        priorityInterpreter?.close()
        priorityInterpreter = null
        priorityInterpreterInitError = null
        priorityVocabulary = null
        _binding = null
        super.onDestroyView()
    }
}
