package com.example.mobilecomputing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 노크 패턴 감지 매니저
 * 가속도계와 자이로스코프 센서를 이용하여 노크 패턴을 감지합니다.
 */
class KnockPatternDetector(private val context: Context) : SensorEventListener {
    private val TAG = "KnockPatternDetector"
    
    // 센서 관련 변수
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // 노크 감지 변수
    private var lastKnockTime: Long = 0
    private val knockThreshold = 15.0f // 노크 감지 임계값
    private val knockCooldown = 300L // 노크 감지 간 최소 간격 (밀리초)
    
    // 노크 패턴 저장 변수
    private val knockPattern = mutableListOf<Long>()
    private val patternTimeout = 2000L // 패턴 인식 타임아웃 (밀리초)
    
    // 콜백 인터페이스
    interface OnKnockPatternDetectedListener {
        fun onKnockPatternDetected(pattern: List<Long>)
    }
    
    private var knockPatternListener: OnKnockPatternDetectedListener? = null
    
    /**
     * 센서 초기화 및 등록
     */
    fun initialize() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        if (accelerometer == null) {
            Log.e(TAG, "가속도계 센서를 사용할 수 없습니다")
        }
        
        if (gyroscope == null) {
            Log.e(TAG, "자이로스코프 센서를 사용할 수 없습니다")
        }
    }
    
    /**
     * 센서 리스너 등록
     */
    fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    /**
     * 센서 리스너 해제
     */
    fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }
    
    /**
     * 노크 패턴 감지 리스너 설정
     */
    fun setOnKnockPatternDetectedListener(listener: OnKnockPatternDetectedListener) {
        knockPatternListener = listener
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometerData(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscopeData(event)
        }
    }
    
    /**
     * 가속도계 데이터 처리
     */
    private fun processAccelerometerData(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // 가속도 크기 계산
        val magnitude = sqrt(x*x + y*y + z*z)
        
        // 중력 가속도(약 9.81) 제거하여 순수 가속도 계산
        val acceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)
        
        // 현재 시간
        val currentTime = System.currentTimeMillis()
        
        // 노크 감지 - 임계값을 넘고 이전 노크와 충분한 시간이 지나야 함
        if (acceleration > knockThreshold && (currentTime - lastKnockTime) > knockCooldown) {
            lastKnockTime = currentTime
            Log.d(TAG, "노크 감지됨: $acceleration")
            
            // 첫 노크이거나 타임아웃 이후의 노크인 경우 패턴 초기화
            if (knockPattern.isEmpty() || currentTime - knockPattern.last() > patternTimeout) {
                knockPattern.clear()
            }
            
            // 노크 시간 추가
            knockPattern.add(currentTime)
            
            // 노크 패턴 분석
            analyzeKnockPattern()
        }
    }
    
    /**
     * 자이로스코프 데이터 처리 (필요시 구현)
     */
    private fun processGyroscopeData(event: SensorEvent) {
        // 현재는 가속도만으로 노크 감지
    }
    
    /**
     * 노크 패턴 분석
     */
    private fun analyzeKnockPattern() {
        // 최소 3번의 노크로 패턴 인식
        if (knockPattern.size >= 3) {
            val pattern = calculateIntervals(knockPattern)
            
            // 특정 패턴 확인 (예: 짧게-짧게-길게)
            if (isValidPattern(pattern)) {
                Log.d(TAG, "유효한 노크 패턴 감지됨: $pattern")
                knockPatternListener?.onKnockPatternDetected(pattern)
                knockPattern.clear()
            }
        }
    }
    
    /**
     * 노크 간 간격 계산
     */
    private fun calculateIntervals(timestamps: List<Long>): List<Long> {
        val intervals = mutableListOf<Long>()
        for (i in 1 until timestamps.size) {
            intervals.add(timestamps[i] - timestamps[i-1])
        }
        return intervals
    }
    
    /**
     * 패턴 유효성 확인
     * 현재는 단순히 예시 패턴을 체크합니다.
     */
    private fun isValidPattern(intervals: List<Long>): Boolean {
        // 예시: SOS 패턴 (짧게-짧게-짧게, 길게-길게-길게, 짧게-짧게-짧게)
        // 실제 구현에서는 더 복잡한 패턴 매칭이 필요합니다.
        return intervals.size >= 2
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 필요시 정확도 변경 처리
    }
} 