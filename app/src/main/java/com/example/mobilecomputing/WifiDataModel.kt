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

// WiFi AP 정보를 저장하는 데이터 클래스
data class WifiInfo(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequency: Int,
    val level: Int
) {
    companion object {
        fun fromScanResult(scanResult: ScanResult): WifiInfo {
            return WifiInfo(
                ssid = scanResult.SSID,
                bssid = scanResult.BSSID,
                capabilities = scanResult.capabilities,
                frequency = scanResult.frequency,
                level = scanResult.level
            )
        }
    }

    // CSV 형식으로 변환
    fun toCsvString(): String {
        return "$ssid,$bssid,$capabilities,$frequency,$level"
    }
}

// 특정 위치에서의 WiFi 스캔 데이터
data class WifiLocationData(
    val position: PointF,
    val timestamp: Long,
    val wifiList: List<WifiInfo>
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// 위치 추정 결과 클래스
data class LocationEstimationResult(
    val position: PointF,
    val score: Double,
    val confidence: Double  // 0.0 ~ 1.0 사이의 신뢰도 점수
)

// WiFi 데이터 관리 클래스
class WifiDataManager(private val context: Context) {
    private val dataFileName = "wifi_location_data.json"
    private val gson = Gson()

    // 모든 저장된 위치 데이터
    private var locationDataList: MutableList<WifiLocationData> = mutableListOf()
    
    // 전파 전파 모델 상수 (실내 환경용)
    private val pathLossExponent = 3.0 // 일반적인 실내 환경의 경로 손실 지수
    private val referenceDistance = 1.0 // 1미터 참조 거리
    private val referenceLevel = -40.0 // 1미터 거리에서의 참조 신호 강도 (dBm)
    
    // 노이즈 필터링을 위한 RSSI 임계값
    private val rssiThreshold = -85 // -85dBm 이하의 신호는 노이즈로 간주

    init {
        loadData()
    }

    // 데이터 저장
    fun saveWifiData(position: PointF, wifiList: List<WifiInfo>) {
        val data = WifiLocationData(
            position = position,
            timestamp = System.currentTimeMillis(),
            wifiList = wifiList
        )

        locationDataList.add(data)
        persistData()
    }

    // 특정 위치의 모든 데이터 삭제
    fun deleteDataAtPosition(position: PointF) {
        // 두 위치가 매우 가까우면 같은 위치로 간주 (오차 범위 0.05)
        locationDataList.removeAll {
            Math.abs(it.position.x - position.x) < 0.05 &&
                    Math.abs(it.position.y - position.y) < 0.05
        }
        persistData()
    }
    
    // 모든 WiFi 데이터 삭제
    fun deleteAllData(): Boolean {
        try {
            locationDataList.clear()
            persistData()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // 특정 위치의 데이터 가져오기
    fun getDataAtPosition(position: PointF): List<WifiLocationData> {
        return locationDataList.filter {
            Math.abs(it.position.x - position.x) < 0.05 &&
                    Math.abs(it.position.y - position.y) < 0.05
        }
    }

    // 모든 데이터 가져오기
    fun getAllData(): List<WifiLocationData> {
        return locationDataList.toList()
    }

    // 모든 위치 좌표 가져오기
    fun getAllPositions(): List<PointF> {
        val positions = mutableSetOf<String>() // 중복 방지를 위한 Set
        val result = mutableListOf<PointF>()

        for (data in locationDataList) {
            val key = "${data.position.x},${data.position.y}"
            if (!positions.contains(key)) {
                positions.add(key)
                result.add(data.position)
            }
        }

        return result
    }
    
    // 저장된 데이터 개수 확인
    fun getDataCount(): Int {
        return locationDataList.size
    }
    
    // 개선된 위치 추정 함수
    fun estimateLocation(currentWifiInfos: List<WifiInfo>): LocationEstimationResult? {
        val allData = getAllData()
        if (allData.isEmpty()) {
            return null
        }

        // 노이즈 필터링 - 약한 신호는 제외
        val filteredWifiInfos = currentWifiInfos.filter { it.level > rssiThreshold }
        if (filteredWifiInfos.isEmpty()) {
            return null
        }

        // BSSID를 키로 하는 맵 생성 (빠른 조회를 위해)
        val currentWifiMap = filteredWifiInfos.associateBy { it.bssid }
        
        // 모든 위치 점수 계산
        val locationScores = mutableListOf<Pair<PointF, Double>>()
        
        for (locationData in allData) {
            // 이 위치에서 신호가 강한 AP만 필터링
            val filteredLocationWifi = locationData.wifiList.filter { it.level > rssiThreshold }
            
            if (filteredLocationWifi.isEmpty()) continue
            
            // 신호 강도 기반 가중치 점수 계산
            var totalScore = 0.0
            var matchCount = 0
            
            for (savedWifi in filteredLocationWifi) {
                val currentWifi = currentWifiMap[savedWifi.bssid] ?: continue
                
                // 공통 AP 발견
                matchCount++
                
                // 두 신호 강도의 유사도 점수 계산
                val signalSimilarity = calculateSignalSimilarity(currentWifi.level, savedWifi.level)
                
                // 신호 강도에 따른 가중치 적용
                val weight = calculateSignalWeight(savedWifi.level)
                
                totalScore += signalSimilarity * weight
            }
            
            // 공통 AP가 있는 경우에만 점수 추가
            if (matchCount > 0) {
                // 평균 점수 계산
                val avgScore = totalScore / matchCount
                locationScores.add(Pair(locationData.position, avgScore))
            }
        }
        
        // 점수가 없으면 null 반환
        if (locationScores.isEmpty()) {
            return null
        }
        
        // 점수가 가장 높은 위치 찾기
        locationScores.sortByDescending { it.second }
        val bestMatch = locationScores.first()
        
        // 신뢰도 계산 (최고 점수와 2위 점수의 차이 비율)
        val confidence = if (locationScores.size > 1) {
            val secondBest = locationScores[1]
            val scoreDifference = bestMatch.second - secondBest.second
            val normalizedDifference = scoreDifference / bestMatch.second
            
            // 0.0 ~ 1.0 사이로 제한
            normalizedDifference.coerceIn(0.0, 1.0)
        } else {
            // 비교할 다른 위치가 없으면 중간 신뢰도
            0.5
        }
        
        return LocationEstimationResult(
            position = bestMatch.first,
            score = bestMatch.second,
            confidence = confidence
        )
    }
    
    // 신호 강도 유사도 계산 (0.0 ~ 1.0 사이 값)
    private fun calculateSignalSimilarity(current: Int, saved: Int): Double {
        // 신호 강도 차이의 절대값
        val diff = Math.abs(current - saved)
        
        // 차이가 클수록 유사도는 떨어짐 (최대 차이를 30dBm으로 가정)
        val maxDiff = 30.0
        val similarity = 1.0 - (diff / maxDiff).coerceIn(0.0, 1.0)
        
        return similarity
    }
    
    // 신호 강도에 따른 가중치 계산
    private fun calculateSignalWeight(level: Int): Double {
        // 신호가 강할수록 더 높은 가중치 부여
        // -30dBm: 최고 신호 (가중치 1.0)
        // -90dBm: 최저 신호 (가중치 0.1)
        val normalizedLevel = (level + 30) / 60.0
        return 0.1 + 0.9 * normalizedLevel.coerceIn(0.0, 1.0)
    }
    
    // RSSI를 기반으로 대략적인 거리 추정 (미터 단위)
    fun estimateDistance(rssi: Int): Double {
        // Log-distance path loss 모델 사용
        val ratio = (referenceLevel - rssi) / (10 * pathLossExponent)
        return referenceDistance * 10.0.pow(ratio)
    }

    // CSV 형식으로 모든 데이터 내보내기
    fun exportAllDataToCsv(): String {
        val sb = StringBuilder()

        // 헤더 추가
        sb.appendLine("x,y,timestamp,ssid,bssid,capabilities,frequency,level")

        // 데이터 추가
        for (locationData in locationDataList) {
            val x = locationData.position.x
            val y = locationData.position.y
            val timestamp = locationData.timestamp

            for (wifi in locationData.wifiList) {
                sb.appendLine("$x,$y,$timestamp,${wifi.toCsvString()}")
            }
        }

        return sb.toString()
    }

    // 데이터 저장
    private fun persistData() {
        try {
            val json = gson.toJson(locationDataList)
            context.openFileOutput(dataFileName, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 데이터 로드
    private fun loadData() {
        try {
            val file = File(context.filesDir, dataFileName)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<MutableList<WifiLocationData>>() {}.type
                locationDataList = gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            locationDataList = mutableListOf()
        }
    }
}