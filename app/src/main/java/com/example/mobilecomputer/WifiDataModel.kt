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

// WiFi 데이터 관리 클래스
class WifiDataManager(private val context: Context) {
    private val dataFileName = "wifi_location_data.json"
    private val gson = Gson()

    // 모든 저장된 위치 데이터
    private var locationDataList: MutableList<WifiLocationData> = mutableListOf()

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