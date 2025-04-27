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
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var mapImageView: ImageView
    private lateinit var uploadMapButton: Button
    private lateinit var deleteMapButton: Button
    private lateinit var wardrivingButton: Button
    private lateinit var localizationButton: Button
    private lateinit var exportButton: Button
    private lateinit var coordinateTextView: TextView

    private var currentMapBitmap: Bitmap? = null
    private var displayedBitmap: Bitmap? = null
    private var selectedPoint: PointF? = null

    private lateinit var wifiScanHelper: WifiScanHelper
    private lateinit var wifiDataManager: WifiDataManager

    private val PERMISSION_REQUEST_CODE = 123
    private val IMAGE_PICK_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // 디버깅용 로그
            android.util.Log.d("MainActivity", "onCreate started")

            // 유틸리티 초기화
            wifiScanHelper = WifiScanHelper(this)
            wifiDataManager = WifiDataManager(this)

            // 디버깅용 로그
            android.util.Log.d("MainActivity", "Utilities initialized")

            // 뷰 초기화
            mapImageView = findViewById(R.id.mapImageView)
            uploadMapButton = findViewById(R.id.uploadMapButton)
            deleteMapButton = findViewById(R.id.deleteMapButton)
            wardrivingButton = findViewById(R.id.wardrivingButton)
            localizationButton = findViewById(R.id.localizationButton)
            exportButton = findViewById(R.id.exportButton)
            coordinateTextView = findViewById(R.id.coordinateTextView)

            // 디버깅용 로그
            android.util.Log.d("MainActivity", "Views initialized")

            // 버튼 초기 상태 설정
            updateButtonStates()

            // 권한 요청
            requestPermissions()

            // 지도 업로드 버튼
            uploadMapButton.setOnClickListener {
                openGallery()
            }

            // 지도 삭제 버튼
            deleteMapButton.setOnClickListener {
                deleteMap()
            }

            // 워드라이빙 버튼
            wardrivingButton.setOnClickListener {
                if (selectedPoint != null) {
                    startWardrivingMode()
                } else {
                    Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
                }
            }

            // 위치 추적 버튼
            localizationButton.setOnClickListener {
                startLocalizationMode()
            }

            // 데이터 내보내기 버튼
            exportButton.setOnClickListener {
                exportData()
            }

            // 지도 클릭 이벤트 처리
            mapImageView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && currentMapBitmap != null) {
                    val x = event.x / mapImageView.width
                    val y = event.y / mapImageView.height
                    selectedPoint = PointF(x, y)

                    // 좌표 표시
                    coordinateTextView.text = "Selected coordinates: (${String.format("%.2f", x)}, ${String.format("%.2f", y)})"

                    // 버튼 상태 업데이트
                    updateButtonStates()

                    // 선택된 지점 표시
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
        val mapExists = currentMapBitmap != null
        deleteMapButton.isEnabled = mapExists
        wardrivingButton.isEnabled = mapExists && selectedPoint != null
        localizationButton.isEnabled = mapExists
        exportButton.isEnabled = mapExists
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
                mapImageView.setImageDrawable(null)
                currentMapBitmap = null
                displayedBitmap = null
                selectedPoint = null
                coordinateTextView.text = ""
                updateButtonStates()
                Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun startWardrivingMode() {
        // 워드라이빙 모드 다이얼로그
        AlertDialog.Builder(this)
            .setTitle("WiFi Scan")
            .setMessage("Start scanning?")
            .setPositiveButton("Yes") { _, _ ->
                // WiFi 스캔 시작
                wifiScanHelper.scanWifi { scanResults ->
                    // 스캔 결과 표시
                    showScanResults(scanResults)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScanResults(scanResults: List<ScanResult>) {
        // 스캔 결과 표시 및 저장 여부 확인
        val message = wifiScanHelper.formatScanResults(scanResults).joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("Scanned APs")
            .setMessage(message)
            .setPositiveButton("Save") { _, _ ->
                // 데이터 저장
                saveWifiData(scanResults)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveWifiData(scanResults: List<ScanResult>) {
        val selectedPos = selectedPoint ?: return

        // ScanResult -> WifiInfo 변환
        val wifiInfoList = scanResults.map { WifiInfo.fromScanResult(it) }

        // 데이터 저장
        wifiDataManager.saveWifiData(selectedPos, wifiInfoList)

        // 성공 메시지
        Toast.makeText(this, "WiFi data saved", Toast.LENGTH_SHORT).show()

        // 지도에 마커 업데이트
        updateMapWithMarkers()
    }

    private fun updateMapWithMarkers() {
        val baseBitmap = currentMapBitmap ?: return

        // 복사본 생성
        val bitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)

        // 페인트 설정
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        // 저장된 모든 위치 표시
        val savedPositions = wifiDataManager.getAllPositions()
        for (position in savedPositions) {
            val x = position.x * bitmap.width
            val y = position.y * bitmap.height
            canvas.drawCircle(x, y, 10f, paint)
        }

        // 현재 선택된 위치 표시 (파란색)
        selectedPoint?.let {
            paint.color = Color.BLUE
            val x = it.x * bitmap.width
            val y = it.y * bitmap.height
            canvas.drawCircle(x, y, 15f, paint)
        }

        // 업데이트된 비트맵 표시
        displayedBitmap = bitmap
        mapImageView.setImageBitmap(bitmap)
    }

    private fun startLocalizationMode() {
        // 실시간 위치추적 모드 시작
        wifiScanHelper.scanWifi { scanResults ->
            locatePosition(scanResults)
        }
    }

    // 간단한 위치 추정 알고리즘 구현
    private fun locatePosition(scanResults: List<ScanResult>) {
        val allData = wifiDataManager.getAllData()
        if (allData.isEmpty()) {
            Toast.makeText(this, "No saved WiFi data", Toast.LENGTH_SHORT).show()
            return
        }

        // 현재 스캔된 WiFi 정보
        val currentWifiInfos = scanResults.map { WifiInfo.fromScanResult(it) }
        val currentBssids = currentWifiInfos.map { it.bssid }

        var bestMatch: PointF? = null
        var bestScore = Int.MIN_VALUE

        // 모든 저장된 위치와 비교
        for (locationData in allData) {
            val savedBssids = locationData.wifiList.map { it.bssid }

            // 공통으로 감지된 AP 수 계산 (간단한 유사도 측정)
            val commonAPs = currentBssids.intersect(savedBssids.toSet()).size

            if (commonAPs > bestScore) {
                bestScore = commonAPs
                bestMatch = locationData.position
            }
        }

        if (bestMatch != null) {
            // 위치 표시
            selectedPoint = bestMatch
            coordinateTextView.text = "Estimated location: (${String.format("%.2f", bestMatch.x)}, ${String.format("%.2f", bestMatch.y)})"
            updateMapWithMarkers()

            Toast.makeText(this, "Location estimation complete", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Could not estimate location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportData() {
        val csvData = wifiDataManager.exportAllDataToCsv()

        if (csvData.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        // CSV 파일 생성
        val csvFile = File(getExternalFilesDir(null), "wifi_data.csv")
        try {
            FileOutputStream(csvFile).use {
                it.write(csvData.toByteArray())
            }

            // 이메일로 파일 공유
            shareFileViaEmail(csvFile)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFileViaEmail(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "WiFi Location Data")
            putExtra(Intent.EXTRA_TEXT, "Attached is the collected WiFi location data.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Send Email"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                try {
                    currentMapBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    selectedPoint = null
                    coordinateTextView.text = ""
                    updateButtonStates()
                    updateMapWithMarkers()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}