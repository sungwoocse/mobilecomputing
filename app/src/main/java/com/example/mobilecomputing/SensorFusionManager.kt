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
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        registerSensors()
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
        
        // 걸음 감지 조건
        if (magnitude > 11.0 && currentTime - lastStepTime > 300) {
            stepCount++
            lastStepTime = currentTime
            
            // 위치 업데이트
            updatePosition()
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
    fun resetPosition(x: Float, y: Float) {
        currentX = x
        currentY = y
        stepCount = 0
    }
} 