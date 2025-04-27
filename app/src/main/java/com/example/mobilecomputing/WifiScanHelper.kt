package com.example.mobilecomputing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

class WifiScanHelper(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var scanCallback: ((List<ScanResult>) -> Unit)? = null
    private var consecutiveScanFailures = 0
    private val maxScanRetries = 3
    
    // 신호 세기 범위 정의
    private val signalStrengthRanges = mapOf(
        "Excellent" to -55,
        "Good" to -70,
        "Fair" to -80,
        "Poor" to -90
    )

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                consecutiveScanFailures = 0
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    fun scanWifi(callback: (List<ScanResult>) -> Unit) {
        scanCallback = callback

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            scanFailure()
        }
    }

    // 스캔 결과를 문자열 리스트로 변환 (표시용) - 개선된 버전
    fun formatScanResults(results: List<ScanResult>): List<String> {
        return results.map { result ->
            // 신호 강도 품질 평가
            val signalQuality = evaluateSignalStrength(result.level)
            
            // RSSI를 거리로 대략 변환
            val estimatedDistance = estimateDistanceFromRssi(result.level)
            
            "${result.SSID}; ${result.BSSID}; [${getSecurityType(result.capabilities)}]; " +
                    "${result.frequency} MHz; ${result.level} dBm ($signalQuality, ~${String.format("%.1f", estimatedDistance)}m)"
        }
    }

    // 신호 강도에 따른 품질 평가
    private fun evaluateSignalStrength(level: Int): String {
        return when {
            level >= signalStrengthRanges["Excellent"]!! -> "Excellent"
            level >= signalStrengthRanges["Good"]!! -> "Good"
            level >= signalStrengthRanges["Fair"]!! -> "Fair"
            level >= signalStrengthRanges["Poor"]!! -> "Poor"
            else -> "Very Poor"
        }
    }
    
    // RSSI를 대략적인 거리로 변환 (실내 환경 가정)
    private fun estimateDistanceFromRssi(rssi: Int): Double {
        // 간단한 로그 거리 모델 사용
        // RSSI = -10 * n * log10(d) + A
        // 여기서:
        // n: 경로 손실 지수 (실내 = 3.0)
        // A: 1미터 거리에서의 RSSI (보통 -40 dBm)
        val n = 3.0
        val a = -40.0
        
        // 공식을 d에 대해 풀면: d = 10^((A - RSSI) / (10 * n))
        val exponent = (a - rssi) / (10.0 * n)
        return Math.pow(10.0, exponent)
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults
        
        // 결과를 신호 강도 순으로 정렬 (강한 신호가 먼저)
        val sortedResults = results.sortedByDescending { it.level }
        
        scanCallback?.invoke(sortedResults)

        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // 이미 등록 해제된 경우
            Log.w("WifiScanHelper", "Receiver already unregistered: ${e.message}")
        }
    }

    private fun scanFailure() {
        consecutiveScanFailures++
        
        if (consecutiveScanFailures <= maxScanRetries) {
            // 스캔 재시도
            Log.d("WifiScanHelper", "Scan failed, retry attempt $consecutiveScanFailures of $maxScanRetries")
            
            Handler(Looper.getMainLooper()).postDelayed({
                val success = wifiManager.startScan()
                if (!success && consecutiveScanFailures == maxScanRetries) {
                    // 마지막 시도도 실패한 경우 캐시된 결과 사용
                    scanSuccess()
                }
            }, 1000) // 1초 후 재시도
        } else {
            // 최대 재시도 횟수를 초과하면 캐시된 결과 사용
            Log.w("WifiScanHelper", "Max retries exceeded, using cached results")
            scanSuccess()
        }
    }

    fun getSecurityType(capabilities: String): String {
        return when {
            capabilities.contains("WPA-EAP") -> "WPA-EAP"
            capabilities.contains("WPA-PSK") -> "WPA-PSK"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    }
}