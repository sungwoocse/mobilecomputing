package com.example.mobilecomputing

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    // UI 컴포넌트
    private lateinit var backButton: Button
    private lateinit var testVibrationButton: Button
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button
    
    private lateinit var dotDurationSeekBar: SeekBar
    private lateinit var dashDurationSeekBar: SeekBar
    private lateinit var pauseDurationSeekBar: SeekBar
    private lateinit var vibrationIntensitySeekBar: SeekBar
    
    private lateinit var dotDurationText: TextView
    private lateinit var dashDurationText: TextView
    private lateinit var pauseDurationText: TextView
    private lateinit var vibrationIntensityText: TextView
    
    private lateinit var autoSpaceSwitch: Switch
    private lateinit var hapticFeedbackSwitch: Switch
    private lateinit var soundFeedbackSwitch: Switch
    
    // 설정 저장용 SharedPreferences
    private lateinit var preferences: SharedPreferences
    
    // 진동 매니저
    private lateinit var vibrator: Vibrator
    
    // 설정 기본값
    companion object {
        // 설정 키
        const val PREFS_NAME = "MorseChatbotSettings"
        const val KEY_DOT_DURATION = "dot_duration"
        const val KEY_DASH_DURATION = "dash_duration"
        const val KEY_PAUSE_DURATION = "pause_duration"
        const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
        const val KEY_AUTO_SPACE = "auto_space"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val KEY_SOUND_FEEDBACK = "sound_feedback"
        
        // 기본값
        const val DEFAULT_DOT_DURATION = 100
        const val DEFAULT_DASH_DURATION = 300
        const val DEFAULT_PAUSE_DURATION = 100
        const val DEFAULT_VIBRATION_INTENSITY = 70 // 백분율
        const val DEFAULT_AUTO_SPACE = true
        const val DEFAULT_HAPTIC_FEEDBACK = true
        const val DEFAULT_SOUND_FEEDBACK = false
        
        // 범위
        const val MIN_DURATION = 50
        const val MAX_DOT_DURATION = 300
        const val MAX_DASH_DURATION = 600
        const val MAX_PAUSE_DURATION = 300
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // SharedPreferences 초기화
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // UI 초기화
        initializeUI()
        
        // 진동 초기화
        initializeVibrator()
        
        // 저장된 설정 불러오기
        loadSettings()
    }
    
    private fun initializeUI() {
        // 버튼 초기화
        backButton = findViewById(R.id.settingsBackButton)
        testVibrationButton = findViewById(R.id.testVibrationButton)
        saveButton = findViewById(R.id.saveSettingsButton)
        resetButton = findViewById(R.id.resetSettingsButton)
        
        // SeekBar 초기화
        dotDurationSeekBar = findViewById(R.id.dotDurationSeekBar)
        dashDurationSeekBar = findViewById(R.id.dashDurationSeekBar)
        pauseDurationSeekBar = findViewById(R.id.pauseDurationSeekBar)
        vibrationIntensitySeekBar = findViewById(R.id.vibrationIntensitySeekBar)
        
        // 텍스트뷰 초기화
        dotDurationText = findViewById(R.id.dotDurationText)
        dashDurationText = findViewById(R.id.dashDurationText)
        pauseDurationText = findViewById(R.id.pauseDurationText)
        vibrationIntensityText = findViewById(R.id.vibrationIntensityText)
        
        // 스위치 초기화
        autoSpaceSwitch = findViewById(R.id.autoSpaceSwitch)
        hapticFeedbackSwitch = findViewById(R.id.hapticFeedbackSwitch)
        soundFeedbackSwitch = findViewById(R.id.soundFeedbackSwitch)
        
        // 시크바 범위 설정
        dotDurationSeekBar.max = MAX_DOT_DURATION - MIN_DURATION
        dashDurationSeekBar.max = MAX_DASH_DURATION - MIN_DURATION
        pauseDurationSeekBar.max = MAX_PAUSE_DURATION - MIN_DURATION
        vibrationIntensitySeekBar.max = 100
        
        // 리스너 설정
        setupListeners()
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
    
    private fun setupListeners() {
        // 뒤로가기 버튼
        backButton.setOnClickListener {
            finish()
        }
        
        // 테스트 진동 버튼
        testVibrationButton.setOnClickListener {
            testVibration()
        }
        
        // 저장 버튼
        saveButton.setOnClickListener {
            saveSettings()
        }
        
        // 초기화 버튼
        resetButton.setOnClickListener {
            resetSettings()
        }
        
        // SeekBar 리스너 설정
        dotDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + MIN_DURATION
                dotDurationText.text = "$value ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        dashDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + MIN_DURATION
                dashDurationText.text = "$value ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        pauseDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + MIN_DURATION
                pauseDurationText.text = "$value ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        vibrationIntensitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                vibrationIntensityText.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun loadSettings() {
        // SharedPreferences에서 저장된 설정 불러오기
        val dotDuration = preferences.getInt(KEY_DOT_DURATION, DEFAULT_DOT_DURATION)
        val dashDuration = preferences.getInt(KEY_DASH_DURATION, DEFAULT_DASH_DURATION)
        val pauseDuration = preferences.getInt(KEY_PAUSE_DURATION, DEFAULT_PAUSE_DURATION)
        val vibrationIntensity = preferences.getInt(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)
        val autoSpace = preferences.getBoolean(KEY_AUTO_SPACE, DEFAULT_AUTO_SPACE)
        val hapticFeedback = preferences.getBoolean(KEY_HAPTIC_FEEDBACK, DEFAULT_HAPTIC_FEEDBACK)
        val soundFeedback = preferences.getBoolean(KEY_SOUND_FEEDBACK, DEFAULT_SOUND_FEEDBACK)
        
        // UI에 설정 적용
        dotDurationSeekBar.progress = dotDuration - MIN_DURATION
        dashDurationSeekBar.progress = dashDuration - MIN_DURATION
        pauseDurationSeekBar.progress = pauseDuration - MIN_DURATION
        vibrationIntensitySeekBar.progress = vibrationIntensity
        
        autoSpaceSwitch.isChecked = autoSpace
        hapticFeedbackSwitch.isChecked = hapticFeedback
        soundFeedbackSwitch.isChecked = soundFeedback
        
        // 텍스트 업데이트
        dotDurationText.text = "$dotDuration ms"
        dashDurationText.text = "$dashDuration ms"
        pauseDurationText.text = "$pauseDuration ms"
        vibrationIntensityText.text = "$vibrationIntensity%"
    }
    
    private fun saveSettings() {
        // 현재 UI 값 가져오기
        val dotDuration = dotDurationSeekBar.progress + MIN_DURATION
        val dashDuration = dashDurationSeekBar.progress + MIN_DURATION
        val pauseDuration = pauseDurationSeekBar.progress + MIN_DURATION
        val vibrationIntensity = vibrationIntensitySeekBar.progress
        val autoSpace = autoSpaceSwitch.isChecked
        val hapticFeedback = hapticFeedbackSwitch.isChecked
        val soundFeedback = soundFeedbackSwitch.isChecked
        
        // SharedPreferences에 설정 저장
        val editor = preferences.edit()
        editor.putInt(KEY_DOT_DURATION, dotDuration)
        editor.putInt(KEY_DASH_DURATION, dashDuration)
        editor.putInt(KEY_PAUSE_DURATION, pauseDuration)
        editor.putInt(KEY_VIBRATION_INTENSITY, vibrationIntensity)
        editor.putBoolean(KEY_AUTO_SPACE, autoSpace)
        editor.putBoolean(KEY_HAPTIC_FEEDBACK, hapticFeedback)
        editor.putBoolean(KEY_SOUND_FEEDBACK, soundFeedback)
        editor.apply()
        
        // 저장 확인 메시지
        android.widget.Toast.makeText(this, "설정이 저장되었습니다", android.widget.Toast.LENGTH_SHORT).show()
        
        // 액티비티 종료
        finish()
    }
    
    private fun resetSettings() {
        // 기본값으로 UI 리셋
        dotDurationSeekBar.progress = DEFAULT_DOT_DURATION - MIN_DURATION
        dashDurationSeekBar.progress = DEFAULT_DASH_DURATION - MIN_DURATION
        pauseDurationSeekBar.progress = DEFAULT_PAUSE_DURATION - MIN_DURATION
        vibrationIntensitySeekBar.progress = DEFAULT_VIBRATION_INTENSITY
        
        autoSpaceSwitch.isChecked = DEFAULT_AUTO_SPACE
        hapticFeedbackSwitch.isChecked = DEFAULT_HAPTIC_FEEDBACK
        soundFeedbackSwitch.isChecked = DEFAULT_SOUND_FEEDBACK
        
        // 텍스트 업데이트
        dotDurationText.text = "$DEFAULT_DOT_DURATION ms"
        dashDurationText.text = "$DEFAULT_DASH_DURATION ms"
        pauseDurationText.text = "$DEFAULT_PAUSE_DURATION ms"
        vibrationIntensityText.text = "$DEFAULT_VIBRATION_INTENSITY%"
        
        // 리셋 확인 메시지
        android.widget.Toast.makeText(this, "설정이 기본값으로 초기화되었습니다", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun testVibration() {
        // 테스트 모스 코드 (SOS)
        val testPattern = "... --- ..."
        
        // 현재 설정된 값으로 진동 패턴 생성
        val dotDuration = dotDurationSeekBar.progress + MIN_DURATION
        val dashDuration = dashDurationSeekBar.progress + MIN_DURATION
        val pauseDuration = pauseDurationSeekBar.progress + MIN_DURATION
        val intensity = vibrationIntensitySeekBar.progress
        
        // 진동 강도 계산 (0-255 범위)
        val amplitudeLevel = (255 * intensity / 100).coerceIn(1, 255)
        
        val vibrationPatterns = mutableListOf<Long>()
        val vibrationAmplitudes = mutableListOf<Int>()
        
        // 시작 지연
        vibrationPatterns.add(500)
        vibrationAmplitudes.add(0)
        
        for (i in testPattern.indices) {
            when (testPattern[i]) {
                '.' -> {
                    // 점 진동
                    vibrationPatterns.add(dotDuration.toLong())
                    vibrationAmplitudes.add(amplitudeLevel)
                }
                '-' -> {
                    // 대시 진동
                    vibrationPatterns.add(dashDuration.toLong())
                    vibrationAmplitudes.add(amplitudeLevel)
                }
                ' ' -> {
                    // 공백 (단어 구분)
                    vibrationPatterns.add(pauseDuration.toLong() * 3)
                    vibrationAmplitudes.add(0)
                    continue
                }
            }
            
            // 문자 사이 간격 (마지막 문자 제외)
            if (i < testPattern.length - 1 && testPattern[i+1] != ' ') {
                vibrationPatterns.add(pauseDuration.toLong())
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
} 