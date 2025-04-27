package com.example.mobilecomputing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

class WifiScanHelper(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var scanCallback: ((List<ScanResult>) -> Unit)? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
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

    // 스캔 결과를 문자열 리스트로 변환 (표시용)
    fun formatScanResults(results: List<ScanResult>): List<String> {
        return results.map { result ->
            "${result.SSID}; ${result.BSSID}; [${getSecurityType(result.capabilities)}]; " +
                    "${result.frequency} MHz; ${result.level} dBm"
        }
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults
        scanCallback?.invoke(results)

        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // 이미 등록 해제된 경우
        }
    }

    private fun scanFailure() {
        // 스캔 실패 시 캐시된 결과 사용
        Handler(Looper.getMainLooper()).postDelayed({
            scanSuccess()
        }, 500)
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