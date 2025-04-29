package com.example.mobilecomputing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import android.net.wifi.ScanResult
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var floorMapView: ImageView
    private lateinit var btnUploadMap: Button
    private lateinit var btnDeleteMap: Button
    private lateinit var btnDataCollection: Button
    private lateinit var btnPositioning: Button
    private lateinit var btnExportData: Button
    private lateinit var btnClearData: Button
    private lateinit var tvPositionInfo: TextView

    private var baseMapImage: Bitmap? = null
    private var annotatedMapImage: Bitmap? = null
    private var markedLocation: PointF? = null

    private lateinit var wifiScanner: WifiScanHelper
    private lateinit var dataStorage: WifiDataManager
    private var lastWiFiPosition: PointF? = null

    private val PERMISSION_REQUEST_CODE = 123
    private val IMAGE_PICK_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // 디버깅용 로그
            android.util.Log.d("MainActivity", "onCreate started")

            // Initialize helpers
            wifiScanner = WifiScanHelper(this)
            dataStorage = WifiDataManager(this)
            
            // 센서 초기화는 비활성화 (WiFi 위치만 사용)
            // sensorFusionManager = SensorFusionManager(this)
            // sensorFusionManager.initialize()

            // Debug logging
            android.util.Log.d("MainActivity", "Utilities initialized")

            // Initialize views
            floorMapView = findViewById(R.id.mapImageView)
            btnUploadMap = findViewById(R.id.uploadMapButton)
            btnDeleteMap = findViewById(R.id.deleteMapButton)
            btnDataCollection = findViewById(R.id.wardrivingButton)
            btnPositioning = findViewById(R.id.localizationButton)
            btnExportData = findViewById(R.id.exportButton)
            btnClearData = findViewById(R.id.deleteDataButton)
            tvPositionInfo = findViewById(R.id.coordinateTextView)

            // 디버깅용 로그
            android.util.Log.d("MainActivity", "Views initialized")

            // 버튼 초기 상태 설정
            updateButtonStates()

            // 권한 요청
            requestPermissions()

            // Map selection button
            btnUploadMap.setOnClickListener {
                openGallery()
            }

            // Map removal button
            btnDeleteMap.setOnClickListener {
                deleteMap()
            }

            // Data collection button
            btnDataCollection.setOnClickListener {
                if (markedLocation != null) {
                    initiateDataCollection()
                } else {
                    Toast.makeText(this, "Please select a reference point on the map", Toast.LENGTH_SHORT).show()
                }
            }

            // Positioning button
            btnPositioning.setOnClickListener {
                initiatePositioning()
            }

            // Data export button
            btnExportData.setOnClickListener {
                exportFingerprints()
            }
            
            // Data wiping button
            btnClearData.setOnClickListener {
                clearAllFingerprints()
            }

            // Map touch event handler
            floorMapView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && baseMapImage != null) {
                    val normalizedX = event.x / floorMapView.width
                    val normalizedY = event.y / floorMapView.height
                    markedLocation = PointF(normalizedX, normalizedY)

                    // Display position information
                    tvPositionInfo.text = "Reference point: (${String.format("%.2f", normalizedX)}, ${String.format("%.2f", normalizedY)})"

                    // Check if location already has data
                    offerLocationOptions(PointF(normalizedX, normalizedY))

                    // Update UI controls
                    updateButtonStates()

                    // Update visual markers
                    updateMapWithMarkers()

                    return@setOnTouchListener true
                }
                false
            }

            // 저장된 위치 데이터 표시
            updateMapWithMarkers()
            
            // 디버깅용 로그
            android.util.Log.d("MainActivity", "onCreate completed")
        } catch (e: Exception) {
            // 오류 로그 출력
            android.util.Log.e("MainActivity", "Error occurred: ${e.message}")
            e.printStackTrace()
            
            // 사용자에게 오류 메시지 표시
            Toast.makeText(this, "An error occurred during app initialization: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // 모든 수집 데이터 삭제 기능
    private fun clearAllFingerprints() {
        // Check for existing data
        val fingerprintCount = dataStorage.getFingerprintCount()
        
        if (fingerprintCount == 0) {
            Toast.makeText(this, "No data to clear", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Clear All Fingerprint Data")
            .setMessage("Are you sure you want to delete all WiFi positioning data? This action cannot be undone. ($fingerprintCount records will be permanently removed)")
            .setPositiveButton("Delete Everything") { _, _ ->
                // Attempt data deletion
                if (dataStorage.wipeAllData()) {
                    Toast.makeText(this, "All positioning data has been removed", Toast.LENGTH_SHORT).show()
                    // Refresh map view
                    updateMapWithMarkers()
                    // Update button availability
                    updateButtonStates()
                } else {
                    Toast.makeText(this, "Data removal failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // Handle selection of locations with existing data
    private fun offerLocationOptions(spot: PointF) {
        val fingerprintsHere = dataStorage.getFingerprintsAt(spot)
        
        if (fingerprintsHere.isNotEmpty()) {
            // Location already has data - show options
            AlertDialog.Builder(this)
                .setTitle("Location Options")
                .setItems(arrayOf("Add new fingerprint", "Remove location data", "View fingerprint data", "Cancel")) { _, selection ->
                    when (selection) {
                        0 -> initiateDataCollection() // Add more data
                        1 -> {
                            // Remove fingerprints at this location
                            dataStorage.removeSpotData(spot)
                            Toast.makeText(this, "Location data removed", Toast.LENGTH_SHORT).show()
                            updateMapWithMarkers()
                            updateButtonStates()
                        }
                        2 -> showSpotFingerprints(fingerprintsHere) // View data
                        3 -> {} // Cancel
                    }
                }
                .show()
        }
    }
    
    // Display WiFi fingerprint data for a specific location
    private fun showSpotFingerprints(fingerprints: List<WifiSpotCapture>) {
        if (fingerprints.isEmpty()) return
        
        val contentBuilder = StringBuilder()
        for (capture in fingerprints) {
            contentBuilder.appendLine("Position: (${String.format("%.2f", capture.mapPoint.x)}, ${String.format("%.2f", capture.mapPoint.y)})")
            contentBuilder.appendLine("Timestamp: ${capture.getFormattedDate()}")
            contentBuilder.appendLine("Networks: ${capture.accessPoints.size}")
            contentBuilder.appendLine("--------------------------")
            
            for (network in capture.accessPoints) {
                contentBuilder.appendLine("${network.ssid}; ${network.bssid}; ${network.securityMode}; ${network.freqChannel} MHz; ${network.signalDbm} dBm")
            }
            contentBuilder.appendLine("\n")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Stored Fingerprints")
            .setMessage(contentBuilder.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            android.util.Log.d("MainActivity", "Permission request result received")
            
            val deniedPermissions = permissions.filterIndexed { index, _ -> 
                grantResults[index] != PackageManager.PERMISSION_GRANTED 
            }
            
            if (deniedPermissions.isNotEmpty()) {
                android.util.Log.w("MainActivity", "Denied permissions: ${deniedPermissions.joinToString()}")
                Toast.makeText(this, "All permissions are required for the app to work properly.", Toast.LENGTH_LONG).show()
            } else {
                android.util.Log.d("MainActivity", "All permissions granted")
            }
        }
    }

    private fun updateButtonStates() {
        val isMapLoaded = baseMapImage != null
        val hasFingerprints = dataStorage.getFingerprintCount() > 0
        
        btnDeleteMap.isEnabled = isMapLoaded
        btnDataCollection.isEnabled = isMapLoaded && markedLocation != null
        btnPositioning.isEnabled = isMapLoaded && hasFingerprints
        btnExportData.isEnabled = isMapLoaded && hasFingerprints
        btnClearData.isEnabled = hasFingerprints
    }

    private fun requestPermissions() {
        try {
            android.util.Log.d("MainActivity", "Starting permission request")
            val permissions = arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET
            )
                
            val notGrantedPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (notGrantedPermissions.isNotEmpty()) {
                android.util.Log.d("MainActivity", "Requesting permissions: ${notGrantedPermissions.joinToString()}")
                ActivityCompat.requestPermissions(this, notGrantedPermissions, PERMISSION_REQUEST_CODE)
            } else {
                android.util.Log.d("MainActivity", "All permissions already granted")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during permission request: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    private fun deleteMap() {
        AlertDialog.Builder(this)
            .setTitle("Delete Map")
            .setMessage("Are you sure you want to delete the map?")
            .setPositiveButton("Yes") { _, _ ->
                floorMapView.setImageDrawable(null)
                baseMapImage = null
                annotatedMapImage = null
                markedLocation = null
                tvPositionInfo.text = ""
                updateButtonStates()
                Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun initiateDataCollection() {
        // 현재 선택된 위치가 없으면 무시
        val selectedPoint = markedLocation ?: return
        
        // Start a scan session
        wifiScanner.scanWifi { scanResults ->
            // 필터링 - WUNIST_AAA_5G 와이파이만 사용
            val targetSSID = "WUNIST_AAA_5G"
            val filteredResults = scanResults.filter { it.SSID == targetSSID }
            
            if (filteredResults.isEmpty()) {
                // 타겟 와이파이가 감지되지 않으면 알림
                runOnUiThread {
                    Toast.makeText(this, "No $targetSSID WiFi detected. Data collection skipped.", Toast.LENGTH_LONG).show()
                }
                return@scanWifi
            }
            
            // Convert the filtered scan results to our storage format
            val apReadings = filteredResults.map { WifiInfo.fromScanResult(it) }
            
            // Save to storage
            dataStorage.storeFingerprint(selectedPoint, apReadings)
            
            // Refresh visual markers
            updateMapWithMarkers()
            
            // Show success feedback
            val apCount = apReadings.size
            val strongestAP = apReadings.maxByOrNull { it.signalDbm }
            
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Data collected at (${String.format("%.2f", selectedPoint.x)}, ${String.format("%.2f", selectedPoint.y)})\n" +
                            "$apCount ${targetSSID} APs found, strongest: ${strongestAP?.signalDbm ?: 0} dBm",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            // Log collection details
            Log.d("DataCollection", "Collected $apCount ${targetSSID} APs at $selectedPoint")
        }
    }

    private fun displayNetworkResults(scanResults: List<ScanResult>) {
        // Format scan data for display
        val networkDetails = wifiScanner.formatScanResults(scanResults)
        
        // Arrange by signal strength
        val orderedResults = networkDetails.sortedByDescending { entry ->
            val signalValue = entry.substringAfterLast("; ").substringBefore(" dBm")
            signalValue.toIntOrNull() ?: -100
        }
        
        val displayContent = if (orderedResults.isEmpty()) {
            "No WiFi networks detected in range."
        } else {
            "Detected ${orderedResults.size} wireless networks:\n\n" + orderedResults.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Network Scan Results")
            .setMessage(displayContent)
            .setPositiveButton("Save Fingerprint") { _, _ ->
                // Save fingerprint data
                storeNetworkData(scanResults)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun storeNetworkData(scanResults: List<ScanResult>) {
        val targetLocation = markedLocation ?: return

        // Filter out weak signals
        val reliableNetworks = scanResults.filter { it.level > -90 }
        
        if (reliableNetworks.isEmpty()) {
            Toast.makeText(this, "No reliable WiFi signals detected", Toast.LENGTH_SHORT).show()
            return
        }

        // Transform to data model
        val networkFingerprints = reliableNetworks.map { WifiInfo.fromScanResult(it) }

        // Store fingerprint data
        dataStorage.storeFingerprint(targetLocation, networkFingerprints)

        // Success feedback
        Toast.makeText(this, "WiFi fingerprint saved (${networkFingerprints.size} networks)", Toast.LENGTH_SHORT).show()

        // Refresh map display
        updateMapWithMarkers()
        
        // Update UI control states
        updateButtonStates()
        
        // Show detailed confirmation
        val uniqueAccessPoints = networkFingerprints.distinctBy { it.bssid }.size
        val highQualitySignals = networkFingerprints.count { it.signalDbm > -70 }
        
        AlertDialog.Builder(this)
            .setTitle("Fingerprint Saved")
            .setMessage("Reference point: (${String.format("%.2f", targetLocation.x)}, ${String.format("%.2f", targetLocation.y)})\n\n" +
                    "Networks recorded: ${networkFingerprints.size}\n" +
                    "Unique access points: $uniqueAccessPoints\n" +
                    "High quality signals: $highQualitySignals\n\n" +
                    "For optimal positioning accuracy, collect fingerprints at multiple nearby points.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateMapWithMarkers() {
        val originalMap = baseMapImage ?: return

        // Create working copy of the map
        val annotatedMap = originalMap.copy(Bitmap.Config.ARGB_8888, true)
        val mapCanvas = Canvas(annotatedMap)

        // Configure marker styles
        val markerStyle = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw reference points from stored data
        val mappedLocations = dataStorage.getSampledLocations()
        for (location in mappedLocations) {
            val pixelX = location.x * annotatedMap.width
            val pixelY = location.y * annotatedMap.height
            mapCanvas.drawCircle(pixelX, pixelY, 10f, markerStyle)
        }

        // Highlight currently selected point (in blue)
        markedLocation?.let {
            markerStyle.color = Color.BLUE
            markerStyle.setShadowLayer(5f, 0f, 0f, Color.WHITE) // Add halo effect
            val pixelX = it.x * annotatedMap.width
            val pixelY = it.y * annotatedMap.height
            mapCanvas.drawCircle(pixelX, pixelY, 15f, markerStyle)
        }

        // Display updated map
        annotatedMapImage = annotatedMap
        floorMapView.setImageBitmap(annotatedMap)
    }

    private fun initiatePositioning() {
        // Start real-time positioning
        Toast.makeText(this, "Scanning networks for position estimation...", Toast.LENGTH_SHORT).show()
        
        wifiScanner.scanWifi { scanResults ->
            determineUserLocation(scanResults)
        }
    }

    // Enhanced location tracking algorithm
    private fun determineUserLocation(scanResults: List<ScanResult>) {
        if (scanResults.isEmpty()) {
            Toast.makeText(this, "No WiFi networks detected", Toast.LENGTH_SHORT).show()
            return
        }

        // Show working indicator
        val processingDialog = AlertDialog.Builder(this)
            .setTitle("Calculating Position...")
            .setMessage("Processing WiFi data...")
            .setCancelable(false)
            .create()
        
        processingDialog.show()

        // Background processing thread
        Thread {
            try {
                // 필터링 기준 완화 (더 많은 AP 사용)
                val accessPointReadings = scanResults.map { WifiInfo.fromScanResult(it) }
                
                // AP 정보 로깅
                android.util.Log.d("WiFiData", "Detected ${accessPointReadings.size} APs")
                accessPointReadings.sortedByDescending { it.signalDbm }.take(5).forEach { ap ->
                    android.util.Log.d("WiFiData", "${ap.ssid}(${ap.bssid}): ${ap.signalDbm}dBm, ${ap.freqChannel}MHz")
                }
                
                // 위치 추정 실행
                val wifiPositioningResult = dataStorage.estimateLocation(accessPointReadings)
                
                runOnUiThread {
                    processingDialog.dismiss()
                    
                    // 위치 업데이트
                    if (wifiPositioningResult != null) {
                        // 최종 위치 (센서 퓨전 비활성화, WiFi 위치만 사용)
                        val finalPosition = wifiPositioningResult.mapPoint
                        
                        // 현재 위치 표시를 위해 markedLocation 업데이트
                        markedLocation = finalPosition
                        
                        // 다음 계산을 위해 현재 위치 저장
                        lastWiFiPosition = finalPosition
                        
                        // UI 업데이트
                        val accuracyPercentage = (wifiPositioningResult.accuracyLevel * 100).roundToInt()
                        val accuracyIndicator = when {
                            accuracyPercentage >= 75 -> "High Accuracy"
                            accuracyPercentage >= 50 -> "Medium Accuracy"
                            accuracyPercentage >= 25 -> "Low Accuracy"
                            else -> "Low Accuracy"
                        }
                        
                        val locationText = "(${String.format("%.2f", finalPosition.x)}, ${String.format("%.2f", finalPosition.y)})"
                        val displayText = "$locationText - $accuracyIndicator ($accuracyPercentage%)"
                        
                        tvPositionInfo.text = displayText
                        
                        // 지도에 위치 표시 업데이트
                        updateMapWithMarkers()
                        
                        // 추가 정보 표시
                        val score = String.format("%.2f", wifiPositioningResult.matchScore)
                        android.util.Log.d("Positioning", "Final position: $finalPosition, Score: $score, Accuracy: $accuracyPercentage%")
                        
                        // 위치 정보 표시
                        showPositioningSuccess(finalPosition, accuracyPercentage, accessPointReadings.size)
                    } else {
                        showPositioningFailed(accessPointReadings.size)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    processingDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }.start()
    }

    // 위치 추정 성공 메시지
    private fun showPositioningSuccess(position: PointF, accuracy: Int, apCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("Position Determined")
            .setMessage("Your estimated position:\n" +
                    "X: ${String.format("%.3f", position.x)}\n" +
                    "Y: ${String.format("%.3f", position.y)}\n\n" +
                    "Confidence: $accuracy%\n" +
                    "APs detected: $apCount\n\n" +
                    "For better accuracy, collect more data points in nearby areas.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPositioningFailed(networkCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("Positioning Failed")
            .setMessage("Unable to determine your current location.\n\n" +
                    "Unique networks found: $networkCount\n" +
                    "Troubleshooting:\n" +
                    "1. Collect fingerprint data from more locations\n" +
                    "2. Verify WiFi is enabled\n" +
                    "3. Ensure you've collected data near your current position")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportFingerprints() {
        // Verify data availability
        val fingerprintCount = dataStorage.getFingerprintCount()
        if (fingerprintCount == 0) {
            Toast.makeText(this, "No positioning data available to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Display export information
        val mappedSpots = dataStorage.getSampledLocations().size
        
        AlertDialog.Builder(this)
            .setTitle("Export Fingerprint Database")
            .setMessage("Prepare to export WiFi positioning database:\n\n" +
                    "• Fingerprint records: $fingerprintCount\n" +
                    "• Reference points: $mappedSpots\n\n" +
                    "Data will be formatted as CSV for analysis.")
            .setPositiveButton("Export") { _, _ ->
                // Show export progress
                val exportDialog = AlertDialog.Builder(this)
                    .setTitle("Processing Data...")
                    .setMessage("Creating export file")
                    .setCancelable(false)
                    .create()
                
                exportDialog.show()
                
                // Background processing
                Thread {
                    val csvOutput = dataStorage.exportAllDataToCsv()
                    
                    runOnUiThread {
                        exportDialog.dismiss()
                        
                        // Generate file with timestamp
                        val timeCode = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        val exportFilename = "indoor_positioning_db_$timeCode.csv"
                        val outputFile = File(getExternalFilesDir(null), exportFilename)
                        
                        try {
                            FileOutputStream(outputFile).use {
                                it.write(csvOutput.toByteArray())
                            }
                            
                            // Send via email
                            sendDataViaEmail(outputFile, mappedSpots, fingerprintCount)
                            
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendDataViaEmail(dataFile: File, locationTotal: Int, recordTotal: Int) {
        try {
            val fileUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                dataFile
            )
            
            val fileSize = String.format("%.2f", dataFile.length() / (1024.0 * 1024.0))
            
            val messageContent = "This file contains WiFi fingerprint data collected for indoor positioning.\n\n" +
                    "Dataset Information:\n" +
                    "• Filename: ${dataFile.name}\n" +
                    "• Size: ${fileSize} MB\n" +
                    "• Reference points: $locationTotal\n" +
                    "• Total readings: $recordTotal\n\n" +
                    "This dataset can be used for indoor positioning algorithm development and testing."

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Indoor Positioning Dataset")
                putExtra(Intent.EXTRA_TEXT, messageContent)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(emailIntent, "Send Data Using"))
            
            // Success notification
            Toast.makeText(this, "Export successful: ${dataFile.name}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                try {
                    baseMapImage = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    markedLocation = null
                    tvPositionInfo.text = ""
                    updateButtonStates()
                    updateMapWithMarkers()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // sensorFusionManager.unregisterSensors()
    }
}