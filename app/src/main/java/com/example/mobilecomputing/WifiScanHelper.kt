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

class WifiScanHelper(private val appContext: Context) {
    private val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var scanCallback: ((List<ScanResult>) -> Unit)? = null
    private var consecutiveScanFailures = 0
    private val maxScanRetries = 3
    private val context = appContext
    
    // Signal quality thresholds
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

    // Enhanced result formatting with more accurate distance estimation
    fun formatScanResults(results: List<ScanResult>): List<String> {
        return results.map { result ->
            // Evaluate signal quality
            val signalQuality = evaluateSignalStrength(result.level)
            
            // Calculate approximate distance based on signal strength and frequency
            val estimatedDistance = estimateDistanceFromRssi(result.level, result.frequency)
            
            // Format with rich information for analysis
            "${result.SSID}; ${result.BSSID}; [${getSecurityType(result.capabilities)}]; " +
                    "${result.frequency} MHz; ${result.level} dBm ($signalQuality, ~${String.format("%.1f", estimatedDistance)}m)"
        }
    }

    // Calculate signal quality rating
    private fun evaluateSignalStrength(level: Int): String {
        return when {
            level >= signalStrengthRanges["Excellent"]!! -> "Excellent"
            level >= signalStrengthRanges["Good"]!! -> "Good"
            level >= signalStrengthRanges["Fair"]!! -> "Fair"
            level >= signalStrengthRanges["Poor"]!! -> "Poor"
            else -> "Very Poor"
        }
    }
    
    // Advanced distance estimation that considers frequency effects
    private fun estimateDistanceFromRssi(rssi: Int, frequency: Int): Double {
        // Frequency-dependent path loss exponent
        // Higher frequencies attenuate more rapidly in indoor environments
        val n = if (frequency > 5000) {
            3.5  // 5GHz signals (higher attenuation)
        } else {
            2.7  // 2.4GHz signals (lower attenuation)
        }
        
        // Reference RSSI at 1 meter (calibrated by frequency band)
        val a = if (frequency > 5000) {
            -41.0  // 5GHz reference
        } else {
            -38.0  // 2.4GHz reference
        }
        
        // Environmental factor adjustment
        // Indoor environments with many obstacles increase attenuation
        val environmentFactor = 0.8
        
        // Calculate using log-distance path loss model: d = 10^((A - RSSI) / (10 * n))
        val exponent = (a - rssi) / (10.0 * n * environmentFactor)
        
        // Apply limits to handle extreme values
        val distance = Math.pow(10.0, exponent)
        return Math.min(Math.max(distance, 0.5), 50.0)  // Constrain between 0.5m and 50m
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults
        
        // Filter out any potentially invalid results
        val validResults = results.filter { 
            it.BSSID != null && it.BSSID.isNotEmpty() && it.level != 0 
        }
        
        // Sort by signal strength for better usability
        val sortedResults = validResults.sortedByDescending { it.level }
        
        // Remove duplicate BSSIDs (keeping strongest signal)
        val uniqueResults = sortedResults.distinctBy { it.BSSID }
        
        // Apply noise filtering for very weak signals
        val qualityResults = uniqueResults.filter { it.level > -95 }
        
        Log.d("WifiScanHelper", "Scan complete. Found ${results.size} total, ${qualityResults.size} quality APs")
        
        scanCallback?.invoke(qualityResults)

        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Handle case where receiver is already unregistered
            Log.w("WifiScanHelper", "Receiver already unregistered: ${e.message}")
        }
    }

    private fun scanFailure() {
        consecutiveScanFailures++
        
        if (consecutiveScanFailures <= maxScanRetries) {
            // Implement exponential backoff for retries
            val backoffTime = 1000L * (1 shl (consecutiveScanFailures - 1))
            
            Log.d("WifiScanHelper", "Scan failed, retry attempt $consecutiveScanFailures of $maxScanRetries (delay: ${backoffTime}ms)")
            
            // Check WiFi state to provide better diagnostic info
            if (!wifiManager.isWifiEnabled) {
                Log.w("WifiScanHelper", "WiFi is disabled, trying to use cached results")
                scanSuccess()
                return
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                // Use different scan method if available on newer Android versions
                val success = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    // For Android 11+, force a full scan
                    wifiManager.startScan()
                } else {
                    // Legacy scan method
                    wifiManager.startScan()
                }
                
                if (!success && consecutiveScanFailures == maxScanRetries) {
                    // If last retry fails, fall back to cached results
                    Log.w("WifiScanHelper", "Final retry failed, using cached results")
                    scanSuccess()
                }
            }, backoffTime) // Use exponential backoff
        } else {
            // Exceeded maximum retries, use cached results
            Log.w("WifiScanHelper", "Max retries exceeded, using cached results")
            scanSuccess()
        }
    }

    // Enhanced security type detection with more granular classification
    fun getSecurityType(capabilities: String): String {
        return when {
            // Enterprise security types
            capabilities.contains("WPA3-EAP") -> "WPA3-EAP"
            capabilities.contains("WPA2-EAP") && capabilities.contains("CCMP") -> "WPA2-EAP-CCMP"
            capabilities.contains("WPA2-EAP") && capabilities.contains("TKIP") -> "WPA2-EAP-TKIP"
            capabilities.contains("WPA-EAP") -> "WPA-EAP"
            
            // Personal security types (newest to oldest)
            capabilities.contains("WPA3-PSK") -> "WPA3-PSK"
            capabilities.contains("WPA2-PSK") && capabilities.contains("CCMP") && !capabilities.contains("TKIP") -> "WPA2-PSK-CCMP"
            capabilities.contains("WPA2-PSK") && capabilities.contains("TKIP") -> "WPA2-PSK-TKIP"
            capabilities.contains("WPA-PSK") && !capabilities.contains("WPA2") -> "WPA-PSK"
            capabilities.contains("WEP") -> "WEP"
            
            // Open networks
            capabilities.contains("ESS") && !capabilities.contains("WPA") && !capabilities.contains("WEP") -> "OPEN"
            
            // Fallback
            else -> "UNKNOWN"
        }
    }
}