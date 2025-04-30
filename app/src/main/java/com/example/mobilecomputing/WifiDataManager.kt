package com.example.mobilecomputing

import android.content.Context
import android.graphics.PointF
import android.net.wifi.ScanResult
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// AP 정보 데이터 클래스
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
            abs(it.mapPoint.x - targetPoint.x) < proximityThreshold &&
                    abs(it.mapPoint.y - targetPoint.y) < proximityThreshold
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
            abs(it.mapPoint.x - targetPoint.x) < proximityThreshold &&
                    abs(it.mapPoint.y - targetPoint.y) < proximityThreshold
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

    // 개선된 위치 추정 알고리즘
    fun estimateLocation(currentReadings: List<WifiInfo>): PositioningResult? {
        // 저장된 지문 데이터 가져오기
        val savedFingerprints = getAllFingerprints()
        if (savedFingerprints.isEmpty()) {
            return null
        }

        // 필터링 기준을 완화하여 더 많은 AP 활용 (-90dBm 이상)
        val usableSignals = currentReadings.filter { it.signalDbm > -90 }
        if (usableSignals.isEmpty()) {
            return null
        }

        // AP 선택 개수 확대 (다양성 향상)
        val stableAPs = selectStableAPs(usableSignals, 20)
        if (stableAPs.size < 3) {  // 최소 3개 AP 필요 (임계값 향상)
            Log.d("WifiPositioning", "Not enough stable APs found for positioning (${stableAPs.size} < 3)")
            return null
        }
        
        // 현재 AP 맵 생성 (빠른 검색용)
        val currentApMap = stableAPs.associateBy { it.bssid }
        
        // AP 특이성 분석 (고유한 AP일수록 높은 가중치 부여)
        val apRarityFactors = calculateApRarityFactors(savedFingerprints, stableAPs)
        
        // 모든 참조 지점에 대한 유사도 계산
        val locationSimilarities = mutableListOf<Triple<PointF, Double, Int>>()
        
        // 위치별 지문 그룹화 (같은 위치의 여러 지문 합치기)
        val locationGroups = savedFingerprints.groupBy { it.mapPoint }
        
        // 각 위치에 대한 유사도 점수 계산
        for ((location, fingerprints) in locationGroups) {
            var bestLocationScore = 0.0
            var apMatchCount = 0
            var bestSignalDeviation = Double.MAX_VALUE
            
            // 해당 위치의 모든 지문에 대해 비교
            for (fingerprint in fingerprints) {
                // 상위 강한 신호 AP 선택
                val referenceAPs = selectStableAPs(fingerprint.accessPoints, 20)
                
                // 유사도 계산 변수
                var matchScore = 0.0
                var matchedAPCount = 0
                var totalSignalDeviation = 0.0
                
                // AP별 유사도 계산
                for (refAP in referenceAPs) {
                    val currAP = currentApMap[refAP.bssid]
                    if (currAP != null) {
                        // 신호 강도 차이 계산
                        val signalDiff = abs(currAP.signalDbm - refAP.signalDbm)
                        totalSignalDeviation += signalDiff
                        
                        // 주파수 대역 일치 보너스 (2.4GHz/5GHz 일치 시 더 높은 점수)
                        val freqMatchBonus = if (isFrequencyBandMatch(currAP.freqChannel, refAP.freqChannel)) 1.2 else 1.0
                        
                        // 강한 신호일수록 더 높은 가중치 (신호가 강할수록 신뢰성 높음)
                        val signalStrengthWeight = calculateSignalWeight(refAP.signalDbm)
                        
                        // AP 희소성 가중치 적용
                        val rarityBonus = (apRarityFactors[refAP.bssid] ?: 1.0) * 1.2
                        
                        // 지수 감쇠 함수로 유사도 계산 - 수정: 감쇠 계수 12.0으로 감소하여 더 정확한 매칭 요구
                        val similarity = exp(-signalDiff / 12.0) * freqMatchBonus * signalStrengthWeight * rarityBonus
                        
                        matchScore += similarity
                        matchedAPCount++
                    }
                }
                
                // 충분한 AP 매칭이 있는지 확인 - 최소 3개로 강화
                if (matchedAPCount >= 3) {
                    // 평균 유사도 점수 계산
                    val avgScore = matchScore / matchedAPCount
                    
                    // AP 매칭 비율 보너스
                    val matchRatio = matchedAPCount.toDouble() / max(referenceAPs.size, stableAPs.size)
                    val matchRatioBonus = 0.8 + (0.2 * matchRatio)
                    
                    // 평균 신호 편차 계산 (낮을수록 좋음)
                    val avgSignalDeviation = if (matchedAPCount > 0) totalSignalDeviation / matchedAPCount else Double.MAX_VALUE
                    
                    // 총점 계산 - 신호 편차 정보 반영 - 편차 영향력 증가
                    val totalScore = avgScore * matchRatioBonus * (1.0 + 0.3 * (1.0 - min(1.0, avgSignalDeviation / 25.0)))
                    
                    // 현재 위치의 최고 점수 갱신
                    if (totalScore > bestLocationScore) {
                        bestLocationScore = totalScore
                        apMatchCount = matchedAPCount
                        bestSignalDeviation = avgSignalDeviation
                    }
                }
            }
            
            // 충분한 유사도가 있는 경우만 후보 위치로 추가 - 임계값 상향 조정
            if (bestLocationScore > 0.35) {
                locationSimilarities.add(Triple(location, bestLocationScore, apMatchCount))
            }
        }
        
        // 유사도 기준으로 정렬
        locationSimilarities.sortByDescending { it.second }
        
        // 위치 추정 실패 처리
        if (locationSimilarities.isEmpty()) {
            return null
        }
        
        // 최적의 K값 선택 - 임계값 상향 조정
        val topScore = locationSimilarities.first().second
        val significantMatches = locationSimilarities.takeWhile { 
            it.second >= topScore * 0.70  // 상위 70% 이상 점수만 사용
        }
        
        // 최소 1개, 최대 5개 위치로 제한
        val matchesToUse = when {
            significantMatches.size > 1 -> significantMatches.take(5)
            else -> locationSimilarities.take(min(3, locationSimilarities.size))
        }
        
        // 가중 평균 위치 계산 - 가중치 함수 조정
        var weightedXSum = 0.0
        var weightedYSum = 0.0
        var weightSum = 0.0
        
        for ((pos, score, _) in matchesToUse) {
            // 가중치 계산 - 변경: 2.0승 함수로 높은 점수에 더 민감하게
            val weight = score.pow(2.0) 
            
            weightedXSum += pos.x * weight
            weightedYSum += pos.y * weight
            weightSum += weight
        }
        
        // 최종 위치 계산
        val estimatedPosition = if (weightSum > 0) {
            PointF(
                (weightedXSum / weightSum).toFloat(),
                (weightedYSum / weightSum).toFloat()
            )
        } else {
            locationSimilarities.first().first
        }
        
        // 경계 안에 위치 제한 (0.0 ~ 1.0)
        val constrainedPosition = PointF(
            estimatedPosition.x.coerceIn(0f, 1f),
            estimatedPosition.y.coerceIn(0f, 1f)
        )
        
        // 정확도 계산 개선
        val accuracy = calculateEnhancedAccuracy(matchesToUse, topScore, stableAPs.size)
        
        // 디버그 로그
        Log.d("WifiPositioning", "Estimated position: $constrainedPosition, Accuracy: $accuracy")
        Log.d("WifiPositioning", "Considered ${matchesToUse.size} locations for positioning")
        Log.d("WifiPositioning", "Top matches: ${matchesToUse.take(3).map { "(${it.first.x.format(2)}, ${it.first.y.format(2)}) - ${it.second.format(2)}" }}")
        
        return PositioningResult(
            mapPoint = constrainedPosition,
            matchScore = topScore,
            accuracyLevel = accuracy
        )
    }

    // Float 포맷팅 확장 함수
    private fun Float.format(digits: Int) = String.format("%.${digits}f", this)
    
    // Double 포맷팅 확장 함수
    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    // 안정적인 AP 선택 함수 개선 (최대 AP 수를 파라미터로 변경)
    private fun selectStableAPs(readings: List<WifiInfo>, maxApCount: Int = 10): List<WifiInfo> {
        // 원하는 SSID만 필터링 (WUNIST_AAA_5G)
        val targetSSID = "WUNIST_AAA_5G"
        val filteredReadings = readings.filter { it.ssid == targetSSID }
        
        // 특정 SSID만 사용 (엄격 모드)
        // 결과가 없으면 빈 리스트 반환 (다른 AP는 사용하지 않음)
        if (filteredReadings.isEmpty()) {
            Log.d("WifiDataManager", "No target SSID ($targetSSID) found - skipping positioning")
            return emptyList()
        }
        
        // 모든 관측에 대해 로깅 (디버깅용)
        Log.d("WifiDataManager", "원본 AP 목록 (${readings.size}개): ${readings.map { it.ssid }.distinct().joinToString()}")
        Log.d("WifiDataManager", "필터링된 AP 목록 (${filteredReadings.size}개): ${filteredReadings.map { "${it.ssid} (${it.signalDbm}dBm)" }.joinToString()}")
        
        // 신호 강도 기준으로 정렬
        val sortedByStrength = filteredReadings.sortedByDescending { it.signalDbm }
        
        // 중복 BSSID 제거 (MAC 주소가 같은 AP는 하나만 사용)
        val uniqueAPs = sortedByStrength.distinctBy { it.bssid }
        
        // 최대 지정된 개수의 AP 사용
        val topAPs = uniqueAPs.take(min(maxApCount, uniqueAPs.size))
        
        // 너무 약한 신호는 제외 (-85dBm 이하는 불안정)
        val reliableAPs = topAPs.filter { it.signalDbm > -85 }
        
        // 최소 3개 AP 확보 (없으면 상위 사용)
        return if (reliableAPs.size >= 3) {
            Log.d("WifiDataManager", "Using ${reliableAPs.size} APs from target SSID")
            reliableAPs
        } else {
            Log.d("WifiDataManager", "Using ${topAPs.size} APs from target SSID (weaker signals)")
            topAPs.take(min(maxApCount, topAPs.size))
        }
    }
    
    // AP 희소성 가중치 계산 추가
    private fun calculateApRarityFactors(savedFingerprints: List<WifiSpotCapture>, currentAPs: List<WifiInfo>): Map<String, Double> {
        val apFrequencyMap = mutableMapOf<String, Int>()
        val apRarityFactors = mutableMapOf<String, Double>()
        
        // 모든 위치에서 각 AP가 발견되는 빈도 계산
        for (fp in savedFingerprints) {
            for (ap in fp.accessPoints) {
                val count = apFrequencyMap.getOrDefault(ap.bssid, 0)
                apFrequencyMap[ap.bssid] = count + 1
            }
        }
        
        // 희소성 가중치 계산
        for (ap in currentAPs) {
            val frequency = apFrequencyMap.getOrDefault(ap.bssid, 0).toDouble()
            if (frequency > 0) {
                // 로그 스케일로 희소성 계산 (빈도 낮을수록 가중치 높음) - 가중치 증가
                val rarityFactor = 1.0 + 0.8 * (1.0 - min(1.0, frequency / savedFingerprints.size))
                apRarityFactors[ap.bssid] = rarityFactor
            } else {
                // 처음 보는 AP는 중간 가중치 부여
                apRarityFactors[ap.bssid] = 1.2
            }
        }
        
        return apRarityFactors
    }

    // 신호 강도에 따른 가중치 계산
    private fun calculateSignalWeight(signalDbm: Int): Double {
        // 강한 신호(-30dBm)는 가중치 1.0, 약한 신호(-85dBm)는 가중치 0.3 - 강도별 차이 증가
        return 0.2 + 0.8 * (min(max(signalDbm + 85, 0), 55) / 55.0)
    }

    // 주파수 대역 일치 여부 확인
    private fun isFrequencyBandMatch(freq1: Int, freq2: Int): Boolean {
        // 2.4GHz 대역 (2412-2484MHz)
        val is1_2GHz = freq1 in 2412..2484
        val is2_2GHz = freq2 in 2412..2484
        
        // 5GHz 대역 (5170-5825MHz)
        val is1_5GHz = freq1 in 5170..5825
        val is2_5GHz = freq2 in 5170..5825
        
        // 같은 대역이면 true
        return (is1_2GHz && is2_2GHz) || (is1_5GHz && is2_5GHz)
    }

    // 개선된 정확도 계산 함수
    private fun calculateEnhancedAccuracy(matches: List<Triple<PointF, Double, Int>>, topScore: Double, apCount: Int): Double {
        // 단일 매치면 낮은 정확도
        if (matches.size <= 1) return 0.3
        
        // 1. 점수 차이 비율 (1등과 2등의 점수 차이가 클수록 좋음)
        val scoreGap = (matches[0].second - matches[1].second) / matches[0].second
        
        // 2. 위치 분산 (가까울수록 좋음)
        var distanceSum = 0.0
        var pairCount = 0
        for (i in 1 until min(min(4, matches.size), matches.size)) {
            val p1 = matches[0].first
            val p2 = matches[i].first
            val distance = sqrt(
                (p1.x - p2.x) * (p1.x - p2.x) + 
                (p1.y - p2.y) * (p1.y - p2.y).toDouble()
            )
            distanceSum += distance
            pairCount++
        }
        
        // 평균 거리 계산 및 점수화 (가까울수록 1에 가까움) - 민감도 조정
        val avgDistance = if (pairCount > 0) distanceSum / pairCount else 0.0
        val distanceScore = 1.0 / (1.0 + avgDistance * 6.0)  // 민감도 증가
        
        // 3. AP 매칭 수 (많을수록 좋음) - 중요도 증가
        val apMatchScore = min(1.0, matches[0].third / 8.0)
        
        // 4. 절대 점수 품질 (높을수록 좋음)
        val absoluteScore = min(1.0, topScore * 1.5)
        
        // 5. 총 사용된 AP 수 (많을수록 신뢰성 높음) - 중요도 증가
        val apCountScore = min(1.0, apCount / 10.0)
        
        // 가중 평균으로 최종 정확도 계산 - 더 정확한 계산
        return (scoreGap * 0.30 + distanceScore * 0.25 + apMatchScore * 0.20 + absoluteScore * 0.15 + apCountScore * 0.10)
            .coerceIn(0.1, 0.95)  // 최대값 95%로 제한
    }
    
    // Save data to local storage
    private fun saveToStorage() {
        try {
            val jsonString = jsonParser.toJson(fingerprintRecords)
            val file = File(appContext.filesDir, storageFileName)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e("WifiDataManager", "Error saving data: ${e.message}")
        }
    }
    
    // Load data from local storage
    private fun loadData() {
        try {
            val file = File(appContext.filesDir, storageFileName)
            if (file.exists()) {
                val jsonString = file.readText()
                val type = object : TypeToken<MutableList<WifiSpotCapture>>() {}.type
                fingerprintRecords = jsonParser.fromJson(jsonString, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            Log.e("WifiDataManager", "Error loading data: ${e.message}")
            fingerprintRecords = mutableListOf()
        }
    }
    
    // Export all data as CSV
    fun exportAllDataToCsv(): String {
        val csv = StringBuilder()
        csv.append("x,y,timestamp,ssid,bssid,security,frequency,rssi\n")
        
        // 필터링 적용
        // 좌표당 고품질 AP 데이터만 CSV에 포함하도록 개선
        // 이 부분은 이미 수집된 데이터에는 영향 없음
        for (record in fingerprintRecords) {
            // 각 좌표마다 원하는 SSID 필터링
            val targetSSID = "WUNIST_AAA_5G"
            val filteredAPs = record.accessPoints.filter { it.ssid == targetSSID }
            
            // 위치별 모든 AP 기록
            val apsToExport = if (filteredAPs.isNotEmpty()) {
                // 타겟 SSID가 있으면 해당 AP만 내보내기
                filteredAPs
            } else {
                // 없으면 모든 AP 포함 (레거시 데이터 유지)
                record.accessPoints
            }
            
            for (ap in apsToExport) {
                csv.append("${record.mapPoint.x},${record.mapPoint.y},${record.captureTime},")
                csv.append("${ap.toCsvString()}\n")
            }
        }
        
        return csv.toString()
    }
} 