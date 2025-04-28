package com.example.mobilecomputing

import android.content.Context
import android.graphics.PointF
import android.net.wifi.ScanResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

// AP information data class with more distinctive naming
data class WifiInfo(
    val ssid: String,
    val bssid: String,
    val securityMode: String,
    val freqChannel: Int,
    val signalDbm: Int
) {
    companion object {
        fun fromScanResult(scanResult: ScanResult): WifiInfo {
            return WifiInfo(
                ssid = scanResult.SSID,
                bssid = scanResult.BSSID,
                securityMode = scanResult.capabilities,
                freqChannel = scanResult.frequency,
                signalDbm = scanResult.level
            )
        }
    }

    // Convert to CSV format with changed field names
    fun toCsvString(): String {
        return "$ssid,$bssid,$securityMode,$freqChannel,$signalDbm"
    }
}

// Location snapshot with wifi readings
data class WifiSpotCapture(
    val mapPoint: PointF,
    val captureTime: Long,
    val accessPoints: List<WifiInfo>
) {
    fun getFormattedDate(): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormatter.format(Date(captureTime))
    }
}

// Positioning result with accuracy metrics
data class PositioningResult(
    val mapPoint: PointF,
    val matchScore: Double,
    val accuracyLevel: Double  // 0.0 ~ 1.0 accuracy measurement
)

// Data manager for WiFi fingerprinting
class WifiDataManager(private val appContext: Context) {
    private val storageFileName = "wifi_fingerprint_records.json"
    private val jsonParser = Gson()

    // All collected fingerprints
    private var fingerprintRecords: MutableList<WifiSpotCapture> = mutableListOf()
    
    // Signal propagation parameters
    private val indoorPathLoss = 3.0 // Path loss exponent for indoor spaces
    private val calibrationDist = 1.0 // Reference distance in meters
    private val baselineSignal = -40.0 // Reference signal at 1m (dBm)
    
    // Quality filtering threshold
    private val signalCutoff = -85 // Filter weak signals below this threshold

    init {
        loadData()
    }

    // Store fingerprint data
    fun storeFingerprint(spot: PointF, apReadings: List<WifiInfo>) {
        val spotData = WifiSpotCapture(
            mapPoint = spot,
            captureTime = System.currentTimeMillis(),
            accessPoints = apReadings
        )

        fingerprintRecords.add(spotData)
        saveToStorage()
    }

    // Remove data at specific location
    fun removeSpotData(targetPoint: PointF) {
        // Consider spots within error margin (5cm in normalized coords)
        val proximityThreshold = 0.05
        
        fingerprintRecords.removeAll {
            Math.abs(it.mapPoint.x - targetPoint.x) < proximityThreshold &&
                    Math.abs(it.mapPoint.y - targetPoint.y) < proximityThreshold
        }
        saveToStorage()
    }
    
    // Wipe all fingerprint data
    fun wipeAllData(): Boolean {
        try {
            fingerprintRecords.clear()
            saveToStorage()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Retrieve data for specific location
    fun getFingerprintsAt(targetPoint: PointF): List<WifiSpotCapture> {
        val proximityThreshold = 0.05
        
        return fingerprintRecords.filter {
            Math.abs(it.mapPoint.x - targetPoint.x) < proximityThreshold &&
                    Math.abs(it.mapPoint.y - targetPoint.y) < proximityThreshold
        }
    }

    // Get all stored fingerprints
    fun getAllFingerprints(): List<WifiSpotCapture> {
        return fingerprintRecords.toList()
    }

    // Get unique sampling locations
    fun getSampledLocations(): List<PointF> {
        val uniqueCoords = mutableSetOf<String>() // Track unique locations
        val mappedPoints = mutableListOf<PointF>()

        for (record in fingerprintRecords) {
            val pointKey = "${record.mapPoint.x},${record.mapPoint.y}"
            if (!uniqueCoords.contains(pointKey)) {
                uniqueCoords.add(pointKey)
                mappedPoints.add(record.mapPoint)
            }
        }

        return mappedPoints
    }
    
    // Count total records
    fun getFingerprintCount(): Int {
        return fingerprintRecords.size
    }
    
    // Enhanced positioning algorithm with WKNN implementation
    fun estimateLocation(currentReadings: List<WifiInfo>): PositioningResult? {
        val referenceData = getAllFingerprints()
        if (referenceData.isEmpty()) {
            return null
        }

        // Filter weak signals for better quality
        val strongSignals = currentReadings.filter { it.signalDbm > signalCutoff }
        if (strongSignals.isEmpty()) {
            return null
        }

        // Create lookup map for current APs
        val visibleApMap = strongSignals.associateBy { it.bssid }
        
        // Calculate similarity scores for all reference points
        val locationMatches = mutableListOf<Pair<PointF, Double>>()
        
        for (refPoint in referenceData) {
            // Get only strong signals from reference data
            val refPointSignals = refPoint.accessPoints.filter { it.signalDbm > signalCutoff }
            
            if (refPointSignals.isEmpty()) continue
            
            // Enhanced scoring system
            var matchScore = 0.0
            var weightTotal = 0.0
            var commonApCount = 0
            
            for (refAp in refPointSignals) {
                val currAp = visibleApMap[refAp.bssid] ?: continue
                
                // Count common APs
                commonApCount++
                
                // Calculate exponential signal similarity
                val signalDelta = Math.abs(currAp.signalDbm - refAp.signalDbm)
                // Higher similarity for smaller differences
                val signalMatch = Math.exp(-signalDelta / 15.0)
                
                // Non-linear weighting for signal strength
                val signalWeight = calculateNonLinearWeight(refAp.signalDbm)
                
                // Weighted scoring
                matchScore += signalMatch * signalWeight
                weightTotal += signalWeight
            }
            
            // Only include locations with sufficient common APs
            val minRequiredAps = Math.min(3, strongSignals.size / 2)
            if (commonApCount >= minRequiredAps && weightTotal > 0) {
                // Normalize and add AP count bonus
                val normalizedMatch = matchScore / weightTotal
                val apBonus = Math.min(1.0, commonApCount / 10.0)
                val finalMatchScore = normalizedMatch * (0.7 + 0.3 * apBonus)
                
                locationMatches.add(Pair(refPoint.mapPoint, finalMatchScore))
            }
        }
        
        // Check if we have any matches
        if (locationMatches.isEmpty()) {
            return null
        }
        
        // Sort by match quality
        locationMatches.sortByDescending { it.second }
        
        // Apply WKNN algorithm with top K points
        val k = Math.min(3, locationMatches.size)
        
        // Calculate weighted average position
        var xWeightedSum = 0.0
        var yWeightedSum = 0.0
        var totalWeight = 0.0
        
        for (i in 0 until k) {
            val (location, score) = locationMatches[i]
            // Square scores to emphasize better matches
            val weight = score * score
            
            xWeightedSum += location.x * weight
            yWeightedSum += location.y * weight
            totalWeight += weight
        }
        
        // Calculate final position
        val estimatedLocation = if (totalWeight > 0) {
            PointF(
                (xWeightedSum / totalWeight).toFloat(),
                (yWeightedSum / totalWeight).toFloat()
            )
        } else {
            // Fallback to best match if weights are invalid
            locationMatches.first().first
        }
        
        // Calculate accuracy metric
        val accuracyMetric = if (locationMatches.size > 1) {
            val bestScore = locationMatches[0].second
            val secondBestScore = locationMatches[1].second
            
            // Score differential component
            val scoreDifferential = (bestScore - secondBestScore) / bestScore
            
            // Signal strength component
            val signalQualityFactor = Math.min(1.0, strongSignals.size / 15.0)
            
            // Combined accuracy metric (0.0 - 1.0)
            (0.5 * scoreDifferential + 0.3 * signalQualityFactor + 0.2 * bestScore).coerceIn(0.0, 1.0)
        } else {
            // Medium confidence for single match
            0.5
        }
        
        return PositioningResult(
            mapPoint = estimatedLocation,
            matchScore = locationMatches.first().second,
            accuracyLevel = accuracyMetric
        )
    }
    
    // Signal importance weighting using sigmoid curve
    private fun calculateNonLinearWeight(signalStrength: Int): Double {
        // Sigmoid-based non-linear weighting
        // Strong signals (~-50dBm): high weight (~1.0)
        // Medium signals (~-75dBm): medium weight (~0.5)
        // Weak signals (~-90dBm): low weight (~0.1)
        
        val centerPoint = -75.0 // Inflection point
        val curveSlope = 0.15 // Controls curve steepness
        
        // Apply sigmoid: 1 / (1 + e^(-slope * (signal - center)))
        val weightCurve = 1.0 / (1.0 + Math.exp(-curveSlope * (signalStrength - centerPoint)))
        
        // Scale to 0.1-1.0 range
        return 0.1 + 0.9 * weightCurve
    }
    
    // Signal similarity with adaptive importance based on signal strength
    private fun calculateSignalSimilarity(measured: Int, reference: Int): Double {
        // Absolute difference between signals
        val strengthDelta = Math.abs(measured - reference)
        
        // Manhattan-style similarity (higher for smaller differences)
        val baseMatchValue = Math.max(0.0, 1.0 - (strengthDelta / 40.0))
        
        // Strong signals deserve higher importance in matching
        val strengthFactor = if (reference > -70) 1.2 else 1.0
        
        return Math.pow(baseMatchValue, strengthFactor)
    }
    
    // RSSI를 기반으로 대략적인 거리 추정 (미터 단위)
    fun estimateDistance(rssi: Int): Double {
        // Log-distance path loss 모델 사용
        val ratio = (referenceLevel - rssi) / (10 * pathLossExponent)
        return referenceDistance * 10.0.pow(ratio)
    }

    // Export all data in standardized CSV format
    fun exportAllDataToCsv(): String {
        val csvBuilder = StringBuilder()

        // Add CSV header
        csvBuilder.appendLine("x,y,timestamp,ssid,bssid,security,frequency,rssi")

        // Add all fingerprint data rows
        for (spot in fingerprintRecords) {
            val xCoord = spot.mapPoint.x
            val yCoord = spot.mapPoint.y
            val timeStamp = spot.captureTime

            for (ap in spot.accessPoints) {
                csvBuilder.appendLine("$xCoord,$yCoord,$timeStamp,${ap.toCsvString()}")
            }
        }

        return csvBuilder.toString()
    }

    // Save data to internal storage
    private fun saveToStorage() {
        try {
            val jsonData = jsonParser.toJson(fingerprintRecords)
            appContext.openFileOutput(storageFileName, Context.MODE_PRIVATE).use {
                it.write(jsonData.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load data from internal storage
    private fun loadData() {
        try {
            val storageFile = File(appContext.filesDir, storageFileName)
            if (storageFile.exists()) {
                val jsonContent = storageFile.readText()
                val dataType = object : TypeToken<MutableList<WifiSpotCapture>>() {}.type
                fingerprintRecords = jsonParser.fromJson(jsonContent, dataType)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fingerprintRecords = mutableListOf()
        }
    }
}