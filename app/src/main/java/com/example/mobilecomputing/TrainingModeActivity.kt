package com.example.mobilecomputing

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TrainingModeActivity : AppCompatActivity() {
    // UI 컴포넌트
    private lateinit var morseInputArea: View
    private lateinit var userInputText: TextView
    private lateinit var targetText: TextView
    private lateinit var currentMorseText: TextView
    private lateinit var instructionText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var challengeButton: Button
    private lateinit var practiceButton: Button
    private lateinit var clearButton: Button
    private lateinit var backButton: Button

    // 모스 코드 변환기
    private lateinit var morseConverter: MorseCodeConverter
    
    // 사용자 입력 패턴 데이터베이스
    private lateinit var patternDatabase: UserInputPatternDatabase

    // 진동 관리자
    private lateinit var vibrator: Vibrator

    // 스케줄러
    private lateinit var scheduler: ScheduledExecutorService

    // 입력 변수
    private var inputStartTime: Long = 0
    private var inputEndTime: Long = 0
    private var currentInput = StringBuilder()
    private var lastTapTime: Long = 0

    // 훈련 모드 변수
    private var targetMorseCode: String = ""
    private var trainingMode: TrainingMode = TrainingMode.NONE
    private var attempts: Int = 0
    private var correctAttempts: Int = 0
    private var totalInputAccuracy: Float = 0f

    // 훈련 단어 목록
    private val practiceWords = listOf(
        "HELLO", "SOS", "HELP", "OK", "YES", "NO", "THANKS",
        "PLEASE", "GOOD", "BAD", "MORSE", "CODE", "LEARN"
    )

    enum class TrainingMode {
        NONE, PRACTICE, CHALLENGE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training_mode)

        // UI 초기화
        initializeUI()

        // 패턴 데이터베이스 초기화
        patternDatabase = UserInputPatternDatabase(this)
        
        // 모스 코드 변환기 초기화 (패턴 데이터베이스 포함)
        morseConverter = MorseCodeConverter(patternDatabase)

        // 진동 초기화
        initializeVibrator()

        // 스케줄러 초기화
        scheduler = Executors.newScheduledThreadPool(1)
    }

    private fun initializeUI() {
        // UI 컴포넌트 연결
        morseInputArea = findViewById(R.id.trainingMorseInputArea)
        userInputText = findViewById(R.id.trainingUserInputText)
        targetText = findViewById(R.id.trainingTargetText)
        currentMorseText = findViewById(R.id.trainingCurrentMorseText)
        instructionText = findViewById(R.id.trainingInstructionText)
        accuracyText = findViewById(R.id.trainingAccuracyText)
        challengeButton = findViewById(R.id.challengeModeButton)
        practiceButton = findViewById(R.id.practiceModeButton)
        clearButton = findViewById(R.id.trainingClearButton)
        backButton = findViewById(R.id.trainingBackButton)

        // 모스 입력 영역 설정
        morseInputArea.setOnTouchListener { _, event ->
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
                                
                                // 입력 완료 확인 (훈련 모드일 때)
                                if (trainingMode != TrainingMode.NONE) {
                                    checkInputAccuracy()
                                }
                            }
                        }
                    }, 1000, TimeUnit.MILLISECONDS)
                    
                    true
                }
                else -> false
            }
        }

        // 버튼 리스너 설정
        practiceButton.setOnClickListener {
            startPracticeMode()
        }

        challengeButton.setOnClickListener {
            startChallengeMode()
        }

        clearButton.setOnClickListener {
            clearInput()
        }

        backButton.setOnClickListener {
            finish()
        }
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

    private fun updateInputDisplay() {
        currentMorseText.text = currentInput.toString()
        
        // 현재 입력을 텍스트로 변환하여 표시
        val currentText = morseConverter.morseToText(currentInput.toString())
        userInputText.text = currentText
    }

    private fun startPracticeMode() {
        trainingMode = TrainingMode.PRACTICE
        attempts = 0
        correctAttempts = 0
        totalInputAccuracy = 0f
        
        // 랜덤 단어 선택
        val randomWord = practiceWords.random()
        targetMorseCode = morseConverter.textToMorse(randomWord)
        
        // 화면 업데이트
        targetText.text = "Word: $randomWord"
        currentMorseText.text = ""
        userInputText.text = ""
        instructionText.text = "Enter Morse code: $targetMorseCode"
        accuracyText.text = "Accuracy: 0%"
        
        // 입력 초기화
        clearInput()
        
        // 모스 코드를 진동으로 출력하여 시연
        playMorseVibration(targetMorseCode)
    }

    private fun startChallengeMode() {
        trainingMode = TrainingMode.CHALLENGE
        attempts = 0
        correctAttempts = 0
        totalInputAccuracy = 0f
        
        // 랜덤 단어 선택 (연습 모드보다 긴 단어)
        val randomWord = practiceWords.random() + " " + practiceWords.random()
        targetMorseCode = morseConverter.textToMorse(randomWord)
        
        // 화면 업데이트
        targetText.text = "Word: $randomWord"
        currentMorseText.text = ""
        userInputText.text = ""
        instructionText.text = "Listen to the Morse code and type it"
        accuracyText.text = "Accuracy: 0%"
        
        // 입력 초기화
        clearInput()
        
        // 모스 코드를 진동으로 출력하여 시연
        playMorseVibration(targetMorseCode)
    }

    private fun clearInput() {
        currentInput.clear()
        updateInputDisplay()
    }

    private fun checkInputAccuracy() {
        val userInput = currentInput.toString().trim()
        if (userInput.isEmpty()) return
        
        // 레벤슈타인 거리 계산으로 유사도 측정
        val accuracy = calculateSimilarity(userInput, targetMorseCode)
        val accuracyPercent = (accuracy * 100).toInt()
        
        // 정확도 업데이트
        attempts++
        totalInputAccuracy += accuracy
        if (accuracy > 0.8f) correctAttempts++
        
        // 평균 정확도 계산
        val avgAccuracy = totalInputAccuracy / attempts
        accuracyText.text = "Accuracy: ${(avgAccuracy * 100).toInt()}% (Success: $correctAttempts/$attempts)"
        
        // 피드백 제공
        if (accuracy > 0.8f) {
            Toast.makeText(this, "Well done! Accuracy: $accuracyPercent%", Toast.LENGTH_SHORT).show()
            
            // 연습 모드면 다음 단어로
            if (trainingMode == TrainingMode.PRACTICE && attempts % 3 == 0) {
                scheduler.schedule({
                    runOnUiThread {
                        startPracticeMode()
                    }
                }, 2000, TimeUnit.MILLISECONDS)
            }
        } else if (accuracy > 0.5f) {
            Toast.makeText(this, "Not bad. Try to be more accurate! Accuracy: $accuracyPercent%", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Try again. Accuracy: $accuracyPercent%", Toast.LENGTH_SHORT).show()
        }
        
        // 입력 초기화
        clearInput()
    }

    // 레벤슈타인 거리 기반 유사도 계산
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                dp[i][j] = when {
                    i == 0 -> j
                    j == 0 -> i
                    s1[i - 1] == s2[j - 1] -> dp[i - 1][j - 1]
                    else -> 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        val maxLength = maxOf(s1.length, s2.length)
        return if (maxLength > 0) 1 - (dp[s1.length][s2.length].toFloat() / maxLength) else 1f
    }

    private fun playMorseVibration(morseCode: String) {
        val dotDuration = 100L
        val dashDuration = 300L
        val pauseDuration = 100L
        val charPauseDuration = 300L
        val wordPauseDuration = 700L
        
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

    override fun onDestroy() {
        super.onDestroy()
        scheduler.shutdown()
    }
} 