package com.example.mobilecomputing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), SensorEventListener {
    // 모스 코드 인식/처리 컴포넌트
    private lateinit var morseCodeInputView: View
    private lateinit var morseCodeInputText: TextView
    private lateinit var morseCodeResponseText: TextView
    private lateinit var trainingModeButton: Button
    private lateinit var settingsButton: Button
    private lateinit var clearButton: Button
    private lateinit var submitButton: Button

    // 모스 코드 처리 변수
    private val dotDuration = 100L // 짧은 진동 (점) 기간 (밀리초)
    private val dashDuration = 300L // 긴 진동 (대시) 기간 (밀리초)
    private val pauseDuration = 100L // 진동 사이 간격 (밀리초)
    private val charPauseDuration = 300L // 문자 사이 간격 (밀리초)
    private val wordPauseDuration = 700L // 단어 사이 간격 (밀리초)

    // 입력 인식 변수
    private var inputStartTime: Long = 0
    private var inputEndTime: Long = 0
    private var currentInput = StringBuilder()
    private var lastTapTime: Long = 0

    // 진동 관리자
    private lateinit var vibrator: Vibrator

    // 백그라운드 작업 스케줄러
    private lateinit var scheduler: ScheduledExecutorService

    // 센서 관리자 (노크 패턴 인식)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // 모스 코드 변환 유틸리티
    private lateinit var morseConverter: MorseCodeConverter

    // AI 모델 클라이언트
    private lateinit var aiModelClient: AIModelClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 초기화
        initializeUI()
        
        // 센서 초기화
        initializeSensors()
        
        // 진동 초기화
        initializeVibrator()
        
        // 모스 코드 변환기 초기화
        morseConverter = MorseCodeConverter()
        
        // AI 모델 클라이언트 초기화
        aiModelClient = AIModelClient(applicationContext)
        
        // 스케줄러 초기화
        scheduler = Executors.newScheduledThreadPool(1)
        
        // 필요한 권한 요청
        requestPermissions()
    }

    private fun initializeUI() {
        morseCodeInputView = findViewById(R.id.morseInputArea)
        morseCodeInputText = findViewById(R.id.inputText)
        morseCodeResponseText = findViewById(R.id.responseText)
        trainingModeButton = findViewById(R.id.trainingButton)
        settingsButton = findViewById(R.id.settingsButton)
        clearButton = findViewById(R.id.clearButton)
        submitButton = findViewById(R.id.submitButton)

        // 모스 코드 입력 영역 터치 리스너 설정
        morseCodeInputView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 터치 시작 시간 기록
                    inputStartTime = System.currentTimeMillis()
                    lastTapTime = inputStartTime
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 터치 종료 시간 기록 및 입력 처리
                    inputEndTime = System.currentTimeMillis()
                    val pressDuration = inputEndTime - inputStartTime
                    
                    // 점(dot) 또는 대시(dash) 결정
                    if (pressDuration < 200) {
                        // 짧은 터치 = 점
                        currentInput.append(".")
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        // 긴 터치 = 대시
                        currentInput.append("-")
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    
                    // 현재 입력 표시 업데이트
                    updateInputDisplay()
                    
                    // 일정 시간 후 문자 구분 인식
                    scheduler.schedule({
                        if (System.currentTimeMillis() - lastTapTime > 1000) {
                            // 문자 구분 추가
                            runOnUiThread {
                                currentInput.append(" ")
                                updateInputDisplay()
                            }
                        }
                    }, 1000, TimeUnit.MILLISECONDS)
                    
                    true
                }
                else -> false
            }
        }

        // 클리어 버튼 리스너
        clearButton.setOnClickListener {
            currentInput.clear()
            updateInputDisplay()
        }

        // 제출 버튼 리스너
        submitButton.setOnClickListener {
            val morseCode = currentInput.toString()
            val text = morseConverter.morseToText(morseCode)
            
            if (text.isNotEmpty()) {
                processUserInput(text)
            } else {
                Toast.makeText(this, "Please enter a valid Morse code", Toast.LENGTH_SHORT).show()
            }
        }

        // 트레이닝 모드 버튼 리스너
        trainingModeButton.setOnClickListener {
            navigateToTrainingMode()
        }

        // 설정 버튼 리스너
        settingsButton.setOnClickListener {
            navigateToSettings()
        }
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    private fun initializeVibrator() {
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.BODY_SENSORS
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun updateInputDisplay() {
        morseCodeInputText.text = currentInput.toString()
    }

    private fun processUserInput(input: String) {
        // 사용자 입력을 AI 모델로 전송하고 응답 받기
        aiModelClient.sendMessage(input) { response ->
            runOnUiThread {
                // 응답 텍스트 표시
                morseCodeResponseText.text = response
                
                // 모스 코드로 변환하여 진동 출력
                val morseResponse = morseConverter.textToMorse(response)
                playMorseVibration(morseResponse)
            }
        }
    }

    private fun playMorseVibration(morseCode: String) {
        val vibrationPatterns = mutableListOf<Long>()
        val vibrationAmplitudes = mutableListOf<Int>()
        
        // 시작 지연
        vibrationPatterns.add(500)
        vibrationAmplitudes.add(0)
        
        for (i in morseCode.indices) {
            when (morseCode[i]) {
                '.' -> {
                    // 점 진동
                    vibrationPatterns.add(dotDuration)
                    vibrationAmplitudes.add(VibrationEffect.DEFAULT_AMPLITUDE)
                }
                '-' -> {
                    // 대시 진동
                    vibrationPatterns.add(dashDuration)
                    vibrationAmplitudes.add(VibrationEffect.DEFAULT_AMPLITUDE)
                }
                ' ' -> {
                    // 공백 (단어 구분)
                    vibrationPatterns.add(wordPauseDuration)
                    vibrationAmplitudes.add(0)
                    continue
                }
            }
            
            // 문자 사이 간격 (마지막 문자 제외)
            if (i < morseCode.length - 1 && morseCode[i+1] != ' ') {
                vibrationPatterns.add(pauseDuration)
                vibrationAmplitudes.add(0)
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(
                vibrationPatterns.toLongArray(),
                vibrationAmplitudes.toIntArray(),
                -1 // 반복 없음
            )
            vibrator.vibrate(vibrationEffect)
        } else {
            // 이전 버전 지원
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun navigateToTrainingMode() {
        // 트레이닝 모드 화면으로 이동
        val intent = Intent(this, TrainingModeActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToSettings() {
        // 설정 화면으로 이동
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // 센서 리스너 등록
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 스케줄러 종료
        scheduler.shutdown()
    }

    // 센서 이벤트 처리 (노크 패턴 인식용)
    override fun onSensorChanged(event: SensorEvent) {
        // TODO: 센서 데이터를 분석하여 노크 패턴 인식 구현
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 정확도 변경 이벤트 처리 (필요시)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}