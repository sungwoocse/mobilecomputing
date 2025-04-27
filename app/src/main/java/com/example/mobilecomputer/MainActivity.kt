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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 유틸리티 초기화
        wifiScanHelper = WifiScanHelper(this)
        wifiDataManager = WifiDataManager(this)

        // 뷰 초기화
        mapImageView = findViewById(R.id.mapImageView)
        uploadMapButton = findViewById(R.id.uploadMapButton)
        deleteMapButton = findViewById(R.id.deleteMapButton)
        wardrivingButton = findViewById(R.id.wardrivingButton)
        localizationButton = findViewById(R.id.localizationButton)
        exportButton = findViewById(R.id.exportButton)
        coordinateTextView = findViewById(R.id.coordinateTextView)

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
                Toast.makeText(this, "지도에서 위치를 선택해주세요", Toast.LENGTH_SHORT).show()
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
                coordinateTextView.text = "선택된 좌표: (${String.format("%.2f", x)}, ${String.format("%.2f", y)})"

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
    }

    private fun updateButtonStates() {
        val mapExists = currentMapBitmap != null
        deleteMapButton.isEnabled = mapExists
        wardrivingButton.isEnabled = mapExists && selectedPoint != null
        localizationButton.isEnabled = mapExists
        exportButton.isEnabled = mapExists
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    private fun deleteMap() {
        AlertDialog.Builder(this)
            .setTitle("지도 삭제")
            .setMessage("지도를 삭제하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                mapImageView.setImageDrawable(null)
                currentMapBitmap = null
                displayedBitmap = null
                selectedPoint = null
                coordinateTextView.text = ""
                updateButtonStates()
                Toast.makeText(this, "지도가 삭제되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun startWardrivingMode() {
        // 워드라이빙 모드 다이얼로그
        AlertDialog.Builder(this)
            .setTitle("WiFi 스캔")
            .setMessage("스캔을 시작하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                // WiFi 스캔 시작
                wifiScanHelper.scanWifi { scanResults ->
                    // 스캔 결과 표시
                    showScanResults(scanResults)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showScanResults(scanResults: List<ScanResult>) {
        // 스캔 결과 표시 및 저장 여부 확인
        val message = wifiScanHelper.formatScanResults(scanResults).joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("스캔된 AP")
            .setMessage(message)
            .setPositiveButton("저장") { _, _ ->
                // 데이터 저장
                saveWifiData(scanResults)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveWifiData(scanResults: List<ScanResult>) {
        val selectedPos = selectedPoint ?: return

        // ScanResult -> WifiInfo 변환
        val wifiInfoList = scanResults.map { WifiInfo.fromScanResult(it) }

        // 데이터 저장
        wifiDataManager.saveWifiData(selectedPos, wifiInfoList)

        // 성공 메시지
        Toast.makeText(this, "WiFi 데이터가 저장되었습니다", Toast.LENGTH_SHORT).show()

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
            Toast.makeText(this, "저장된 WiFi 데이터가 없습니다", Toast.LENGTH_SHORT).show()
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
            coordinateTextView.text = "추정 위치: (${String.format("%.2f", bestMatch.x)}, ${String.format("%.2f", bestMatch.y)})"
            updateMapWithMarkers()

            Toast.makeText(this, "위치 추정 완료", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "위치를 추정할 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportData() {
        val csvData = wifiDataManager.exportAllDataToCsv()

        if (csvData.isEmpty()) {
            Toast.makeText(this, "내보낼 데이터가 없습니다", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "데이터 내보내기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
            putExtra(Intent.EXTRA_SUBJECT, "WiFi 위치 데이터")
            putExtra(Intent.EXTRA_TEXT, "수집된 WiFi 위치 데이터를 첨부합니다.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "이메일 전송하기"))
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
                    Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}