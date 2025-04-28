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
    private val pathLossExponent = 3.0 // Path loss exponent for indoor spaces
    private val referenceDistance = 1.0 // Reference distance in meters
    private val referenceLevel = -40 // Reference signal at 1m (dBm)
    
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
    
    // 개선된 위치 추정 알고리즘 (핑거프린팅 기반 삼각측량)
    fun estimateLocation(currentReadings: List<WifiInfo>): PositioningResult? {
        val savedFingerprints = getAllFingerprints()
        if (savedFingerprints.isEmpty()) {
            return null
        }

        // 약한 신호는 필터링하되, 삼각측량을 위해 충분한 AP 확보
        val usableSignals = currentReadings.filter { it.signalDbm > -90 }
        if (usableSignals.isEmpty()) {
            return null
        }

        // 현재 스캔한 AP 목록 맵으로 변환
        val currentApMap = usableSignals.associateBy { it.bssid }
        
        // 모든 참조점과의 유사도 계산을 위한 리스트
        val positionSimilarities = mutableListOf<Pair<PointF, Double>>()
        
        // AP 특이성 분석 (고유한 AP일수록 높은 가중치 부여)
        val detectedBssids = usableSignals.map { it.bssid }.toSet()
        val apLocationCounts = mutableMapOf<String, Int>()
        for (bssid in detectedBssids) {
            // 각 AP가 몇 개의 참조 위치에서 발견되는지 계산
            apLocationCounts[bssid] = savedFingerprints.count { refSpot ->
                refSpot.accessPoints.any { it.bssid == bssid }
            }
        }
        
        // 특이성 역수 가중치 계산 - 희소한 AP일수록 가중치 증가
        val apUniquenessFactors = mutableMapOf<String, Double>()
        for (bssid in detectedBssids) {
            val spotCount = apLocationCounts[bssid] ?: 0
            if (spotCount > 0) {
                // 루트 역수 가중치로 특이성 계산
                apUniquenessFactors[bssid] = 1.0 / Math.sqrt(spotCount.toDouble())
            } else {
                apUniquenessFactors[bssid] = 1.0
            }
        }
        
        for (savedSpot in savedFingerprints) {
            // 참조 지점의 유효한 신호들만 사용
            val spotApData = savedSpot.accessPoints.filter { it.signalDbm > -90 }
            
            if (spotApData.isEmpty()) continue
            
            // 유사도 점수 계산 시스템
            var matchPoints = 0.0
            var totalWeightSum = 0.0
            var overlappingApCount = 0
            var rssiSum = 0.0
            var bestApMatchQuality = 0.0
            
            for (savedAp in spotApData) {
                val currentAp = currentApMap[savedAp.bssid] ?: continue
                
                // 공통 AP 카운트
                overlappingApCount++
                
                // 신호 강도 차이 기반 지수 감쇠 유사도
                val rssiDifference = Math.abs(currentAp.signalDbm - savedAp.signalDbm)
                
                // 작은 차이는 점진적으로 감소, 큰 차이는 급격히 감소하는 지수 함수
                val apMatchQuality = Math.exp(-rssiDifference / 20.0)
                
                // 이 참조점에서 가장 잘 매칭된 AP 트래킹
                if (apMatchQuality > bestApMatchQuality) {
                    bestApMatchQuality = apMatchQuality
                }
                
                // 신호 강도 기반 가중치 - 강한 신호가 더 신뢰성 높음
                val rssiWeight = calculateNonLinearWeight(savedAp.signalDbm)
                
                // AP 특이성 가중치 적용 - 희소한 AP는 위치 특정에 더 중요
                val uniquenessFactor = apUniquenessFactors[savedAp.bssid] ?: 1.0
                
                // 평균 신호 강도 계산용
                rssiSum += savedAp.signalDbm
                
                // 신호 강도와 특이성을 결합한 최종 가중치
                val apImportanceWeight = rssiWeight * uniquenessFactor
                
                // 가중 점수 누적
                matchPoints += apMatchQuality * apImportanceWeight
                totalWeightSum += apImportanceWeight
            }
            
            // 최소 필요 AP 수 (유연한 임계값)
            val requiredMinApCount = Math.max(2, Math.min(3, usableSignals.size / 3))
            
            if (overlappingApCount >= requiredMinApCount && totalWeightSum > 0) {
                // 가중치로 정규화
                val normalizedScore = matchPoints / totalWeightSum
                
                // AP 커버리지 비율 계산 (많을수록 좋음)
                val apCoverageRate = overlappingApCount.toDouble() / usableSignals.size
                
                // 신호 품질 보너스 (강한 신호일수록 신뢰도 증가)
                val averageRssi = if (overlappingApCount > 0) 
                    rssiSum / overlappingApCount else -90.0
                val signalStrengthBonus = Math.max(0.0, Math.min(0.3, (averageRssi + 100) / 100))
                
                // 다양한 요소 결합한 최종 점수 계산
                // - 기본 유사도 점수 (60%)
                // - 최상위 AP 매치 품질 (20%)
                // - AP 커버리지 보너스 (10%)
                // - 신호 강도 보너스 (10%)
                val finalScore = (normalizedScore * 0.6) + 
                               (bestApMatchQuality * 0.2) +
                               (apCoverageRate * 0.1) +
                               signalStrengthBonus
                
                positionSimilarities.add(Pair(savedSpot.mapPoint, finalScore))
            }
        }
        
        // 매칭 결과 없음 처리
        if (positionSimilarities.isEmpty()) {
            return null
        }
        
        // 유사도 점수로 정렬
        positionSimilarities.sortByDescending { it.second }
        
        // 동적 K-최근접 이웃 알고리즘 - 점수가 비슷한 위치만 선택
        val highestScore = positionSimilarities.first().second
        val autoSelectedK = positionSimilarities.takeWhile { it.second > highestScore * 0.7 }.size
        val optimalK = Math.max(2, Math.min(autoSelectedK, 5)) // 2~5개 이웃으로 제한
        
        // 가중 평균 위치 계산
        var weightedXSum = 0.0
        var weightedYSum = 0.0
        var weightSum = 0.0
        
        for (i in 0 until Math.min(optimalK, positionSimilarities.size)) {
            val (spotPosition, similarityScore) = positionSimilarities[i]
            
            // 세제곱 가중치로 우수 매치 강조 (위치 평균이 하위 매치에 너무 영향받지 않도록)
            val cubicWeight = Math.pow(similarityScore, 3.0)
            
            weightedXSum += spotPosition.x * cubicWeight
            weightedYSum += spotPosition.y * cubicWeight
            weightSum += cubicWeight
        }
        
        // 최종 위치 계산
        val estimatedPosition = if (weightSum > 0) {
            PointF(
                (weightedXSum / weightSum).toFloat(),
                (weightedYSum / weightSum).toFloat()
            )
        } else {
            // 가중치 계산 실패시 가장 유사한 위치 사용
            positionSimilarities.first().first
        }
        
        // 개선된 정확도 측정 지표 계산
        val accuracyRating = if (positionSimilarities.size > 1) {
            val topScore = positionSimilarities[0].second
            val runnerUpScore = positionSimilarities[1].second
            
            // 점수 격차 지표 - 격차가 클수록 확실한 매치
            val scoreSeparation = (topScore - runnerUpScore) / topScore
            
            // 신호 품질 지표 - AP 개수와 신호 강도 반영
            val apQuantityFactor = Math.min(1.0, usableSignals.size / 12.0)
            val avgDbm = usableSignals.map { it.signalDbm }.average()
            val signalQualityMetric = Math.max(0.0, Math.min(1.0, (avgDbm + 100) / 40))
            
            // 위치 군집성 - 상위 매치들이 얼마나 모여있는지 (가까울수록 신뢰도 높음)
            val topPositions = positionSimilarities.take(Math.min(3, positionSimilarities.size)).map { it.first }
            var distanceSum = 0.0
            var pairCount = 0
            
            for (i in 0 until topPositions.size - 1) {
                for (j in i + 1 until topPositions.size) {
                    val xDiff = topPositions[i].x - topPositions[j].x
                    val yDiff = topPositions[i].y - topPositions[j].y
                    val pointDistance = Math.sqrt((xDiff * xDiff + yDiff * yDiff).toDouble())
                    distanceSum += pointDistance
                    pairCount++
                }
            }
            
            val positionClusterFactor = if (pairCount > 0) {
                val meanPairDistance = distanceSum / pairCount
                Math.max(0.0, 1.0 - meanPairDistance * 2.0) // 0.5 거리 = 0 안정성
            } else {
                0.5 // 계산 불가시 중간값
            }
            
            // 정확도 지표 종합 (0.0-1.0 범위)
            (0.3 * scoreSeparation + 
             0.3 * apQuantityFactor + 
             0.2 * signalQualityMetric + 
             0.2 * positionClusterFactor).coerceIn(0.0, 1.0)
        } else {
            // 단일 매치시 낮은-중간 신뢰도
            0.4
        }
        
        return PositioningResult(
            mapPoint = estimatedPosition,
            matchScore = positionSimilarities.first().second,
            accuracyLevel = accuracyRating
        )
    }
    
    // Signal strength based sigmoid weight calculation function
    private fun calculateNonLinearWeight(rssiValue: Int): Double {
        // Signal strength weight ranges:
        // Very strong signals (>-60dBm): very high weight (~0.95-1.0)
        // Strong signals (~-65dBm): high weight (~0.8-0.9)
        // Medium signals (~-75dBm): medium weight (~0.5)
        // Weak signals (~-85dBm): low weight (~0.2-0.3)
        // Very weak signals (<-90dBm): very low weight (~0.1)
        
        val midpoint = -75.0 // Sigmoid curve inflection point
        val slopeCoef = 0.2 // Rate of weight change by signal difference
        
        // Sigmoid function: 1 / (1 + e^(-slope * (signal - midpoint)))
        val calcWeight = 1.0 / (1.0 + Math.exp(-slopeCoef * (rssiValue - midpoint)))
        
        // Scale to 0.05-1.0 range to emphasize signal strength impact
        return 0.05 + 0.95 * calcWeight
    }
    
    // Calculate similarity between two signal fingerprints
    private fun computeFingerprintSimilarity(
        currentSignals: Map<String, Int>,
        referencePoint: WifiSpotCapture,
        apImportance: Map<String, Double>
    ): Double {
        var weightedDistSum = 0.0
        var weightSum = 0.0
        var commonApCount = 0
        
        // Calculate for each AP in reference point
        for (refAp in referencePoint.accessPoints) {
            // Exclude extremely weak signals
            if (refAp.signalDbm < -90) continue
            
            // Check if this AP is also found in current scan
            val currentRssi = currentSignals[refAp.bssid]
            if (currentRssi != null) {
                commonApCount++
                
                // Calculate squared signal strength difference
                val rssiDiff = (currentRssi - refAp.signalDbm).toDouble()
                val squaredDiff = rssiDiff * rssiDiff
                
                // Apply AP rarity weight factor
                val apRarity = apImportance[refAp.bssid] ?: 1.0
                
                // Signal strength reliability weight
                val signalReliability = calculateNonLinearWeight(refAp.signalDbm)
                
                // Combined weight factor
                val combinedWeight = signalReliability * apRarity
                
                // Weighted distance calculation
                weightedDistSum += squaredDiff * combinedWeight
                weightSum += combinedWeight
            }
        }
        
        // Return maximum distance if no matching APs
        if (commonApCount == 0 || weightSum == 0.0) {
            return Double.MAX_VALUE
        }
        
        // Final distance normalized by weights
        val normalizedDist = Math.sqrt(weightedDistSum / weightSum)
        
        // Apply penalty for missing APs
        val matchRatio = commonApCount.toDouble() / 
                       Math.min(referencePoint.accessPoints.size, currentSignals.size)
        val missingPenalty = 1.0 + Math.max(0.0, 1.0 - matchRatio) * 2.0
        
        return normalizedDist * missingPenalty
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