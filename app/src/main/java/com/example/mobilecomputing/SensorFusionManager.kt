package com.example.mobilecomputing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

class SensorFusionManager(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // 센서 데이터 저장
    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastGyroscope = FloatArray(3)
    
    // 방향 및 이동 관련 변수
    private var orientation = FloatArray(3)
    private var stepCount = 0
    private var lastStepTime = 0L
    private var stepLength = 0.7f // 평균 보폭 (미터)
    
    // 필터링 상수
    private val alpha = 0.8f // 가속도계 필터링 계수
    private val beta = 0.1f  // 자이로스코프 필터링 계수
    
    // 현재 위치 추정
    private var currentX = 0f
    private var currentY = 0f
    private var currentHeading = 0f
    
    // 센서 리스너
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // 가속도계 데이터 필터링
                    lastAccelerometer = lowPassFilter(lastAccelerometer, event.values, alpha)
                    
                    // 걸음 감지
                    detectStep(lastAccelerometer)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    lastMagnetometer = lowPassFilter(lastMagnetometer, event.values, alpha)
                    updateOrientation()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroscope = lowPassFilter(lastGyroscope, event.values, beta)
                    updateHeading()
                }
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 정확도 변경 처리
        }
    }
    
    // 초기화
    fun initialize() {
        try {
            // 센서 인스턴스 가져오기
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            
            // 센서 사용 가능성 검사
            val isSensorAvailable = accelerometer != null && magnetometer != null
            
            if (isSensorAvailable) {
                // 센서 등록
                registerSensors()
                
                // 센서 데이터 초기화
                lastAccelerometer = FloatArray(3)
                lastMagnetometer = FloatArray(3)
                lastGyroscope = FloatArray(3)
                orientation = FloatArray(3)
                
                // 로그 기록
                android.util.Log.d("SensorFusion", "Sensors initialized successfully")
            } else {
                // 로그 기록
                android.util.Log.e("SensorFusion", "Required sensors not available on this device")
            }
        } catch (e: Exception) {
            // 예외 처리
            android.util.Log.e("SensorFusion", "Error initializing sensors: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 센서 등록
    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    // 센서 해제
    fun unregisterSensors() {
        sensorManager.unregisterListener(sensorEventListener)
    }
    
    // 걸음 감지 알고리즘
    private fun detectStep(acceleration: FloatArray) {
        val magnitude = sqrt(
            acceleration[0] * acceleration[0] +
            acceleration[1] * acceleration[1] +
            acceleration[2] * acceleration[2]
        )
        
        val currentTime = System.currentTimeMillis()
        
        // 동적 임계값 시스템 구현
        val minMagnitude = 10.0  // 최소 임계값
        val maxMagnitude = 15.0  // 최대 임계값
        
        // 사용자 활동 상태에 따라 임계값 조정
        val activityThreshold = when {
            magnitude > 14.0 -> maxMagnitude  // 달리기 또는 빠른 걸음
            magnitude > 12.0 -> 11.5          // 보통 걸음
            else -> minMagnitude              // 느린 걸음 또는 정지
        }
        
        // 시간 간격 - 빠른 걸음을 감지하기 위해 더 짧은 간격 허용
        val timeThreshold = when {
            magnitude > 14.0 -> 250L  // 빠른 걸음
            magnitude > 12.0 -> 300L  // 보통 걸음
            else -> 400L              // 느린 걸음
        }
        
        // 걸음 감지 조건
        if (magnitude > activityThreshold && currentTime - lastStepTime > timeThreshold) {
            stepCount++
            lastStepTime = currentTime
            
            // 위치 업데이트
            updatePosition()
            
            // 디버깅용 로그 (개발자 모드에서만)
            android.util.Log.d("SensorFusion", "Step detected: $stepCount, Magnitude: $magnitude")
        }
    }
    
    // 방향 업데이트
    private fun updateOrientation() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, 
                lastAccelerometer, lastMagnetometer)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            currentHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        }
    }
    
    // 자이로스코프 기반 방향 업데이트
    private fun updateHeading() {
        val dt = 0.1f // 샘플링 시간 간격
        currentHeading += lastGyroscope[2] * dt
    }
    
    // 위치 업데이트
    private fun updatePosition() {
        val headingRad = Math.toRadians(currentHeading.toDouble())
        currentX += (stepLength * sin(headingRad)).toFloat()
        currentY += (stepLength * cos(headingRad)).toFloat()
    }
    
    // 저역 통과 필터
    private fun lowPassFilter(input: FloatArray, values: FloatArray, alpha: Float): FloatArray {
        val output = FloatArray(3)
        for (i in 0..2) {
            output[i] = input[i] + alpha * (values[i] - input[i])
        }
        return output
    }
    
    // 현재 위치 및 방향 반환
    fun getCurrentPosition(): Triple<Float, Float, Float> {
        return Triple(currentX, currentY, currentHeading)
    }
    
    // 걸음 수 반환
    fun getStepCount(): Int {
        return stepCount
    }
    
    // 위치 초기화
    fun resetPosition(x: Float, y: Float, initialHeading: Float = 0f) {
        currentX = x
        currentY = y
        currentHeading = initialHeading
        stepCount = 0
        
        // 로그 기록
        android.util.Log.d("SensorFusion", "Position reset to ($x, $y, $initialHeading)")
    }
    
    // 센서 가용성 확인
    fun isSensorsAvailable(): Boolean {
        return accelerometer != null && magnetometer != null
    }
} 