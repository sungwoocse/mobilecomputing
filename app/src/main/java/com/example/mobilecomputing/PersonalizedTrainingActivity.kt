package com.example.mobilecomputing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PersonalizedTrainingActivity : AppCompatActivity() {
    // UI 컴포넌트
    private lateinit var instructionText: TextView
    private lateinit var targetWordText: TextView
    private lateinit var currentMorseText: TextView
    private lateinit var userInputText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var wordProgressText: TextView
    private lateinit var morseInputArea: View
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var finishButton: Button

    // 핵심 컴포넌트
    private lateinit var patternDatabase: UserInputPatternDatabase
    private lateinit var morseConverter: MorseCodeConverter
    private lateinit var vibrator: Vibrator
    private lateinit var scheduler: ScheduledExecutorService

    // 트레이닝 변수
    private var currentWordIndex = 0
    private var currentRepetition = 0
    private val maxRepetitions = 10
    private var inputStartTime: Long = 0
    private var lastInputTime: Long = 0
    private var isTrainingActive = false
    
    // 입력 타이밍 데이터
    private val inputTimings = mutableListOf<Pair<Long, Long>>() // (press duration, pause duration)
    private val currentInput = StringBuilder()

    // 트레이닝 단어 목록 (기본적인 패턴 학습용)
    private val trainingWords = listOf(
        "E", "T", "I", "A", "N", "M", "S", "U", "R", "W", "D", "K", "G", "O", "H", "V", "F", "L", "P", "J", "B", "X", "C", "Y", "Z", "Q",
        "SOS", "OK", "HI", "AT", "SO", "DO", "GO", "ME", "WE", "NO", "ON", "UP", "IT", "TO", "BE", "OR", "IN", "AS", "OF", "IF", "IS"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalized_training)

        initializeComponents()
        setupUI()
        startTraining()
    }

    private fun initializeComponents() {
        try {
            // 패턴 데이터베이스 초기화
            patternDatabase = UserInputPatternDatabase(this)
            morseConverter = MorseCodeConverter(patternDatabase)
        } catch (e: Exception) {
            Log.e("PersonalizedTraining", "Error initializing components", e)
            // 기본 모드로 폴백
            morseConverter = MorseCodeConverter()
        }

        // 진동 초기화  
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 스케줄러 초기화
        scheduler = Executors.newScheduledThreadPool(1)
    }

    private fun setupUI() {
        // UI 컴포넌트 연결
        instructionText = findViewById(R.id.personalizedInstructionText)
        targetWordText = findViewById(R.id.personalizedTargetWordText)
        currentMorseText = findViewById(R.id.personalizedCurrentMorseText)
        userInputText = findViewById(R.id.personalizedUserInputText)
        progressText = findViewById(R.id.personalizedProgressText)
        progressBar = findViewById(R.id.personalizedProgressBar)
        wordProgressText = findViewById(R.id.personalizedWordProgressText)
        morseInputArea = findViewById(R.id.personalizedMorseInputArea)
        nextButton = findViewById(R.id.personalizedNextButton)
        skipButton = findViewById(R.id.personalizedSkipButton)
        finishButton = findViewById(R.id.personalizedFinishButton)

        // 모스 입력 영역 설정
        morseInputArea.setOnTouchListener { _, event ->
            if (!isTrainingActive) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inputStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val inputEndTime = System.currentTimeMillis()
                    val pressDuration = inputEndTime - inputStartTime
                    val pauseDuration = if (lastInputTime > 0) inputStartTime - lastInputTime else 0L
                    
                    // 입력 타이밍 기록
                    inputTimings.add(Pair(pressDuration, pauseDuration))
                    lastInputTime = inputEndTime
                    
                    // dot/dash 분류 및 학습 데이터 저장 (안전한 처리)
                    val inputType = if (::patternDatabase.isInitialized) {
                        patternDatabase.classifyInput(pressDuration)
                    } else {
                        if (pressDuration < 200) UserInputPatternDatabase.InputType.DOT else UserInputPatternDatabase.InputType.DASH
                    }
                    
                    when (inputType) {
                        UserInputPatternDatabase.InputType.DOT -> {
                            currentInput.append(".")
                            if (::patternDatabase.isInitialized) {
                                patternDatabase.recordDotDuration(pressDuration)
                            }
                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        UserInputPatternDatabase.InputType.DASH -> {
                            currentInput.append("-")
                            if (::patternDatabase.isInitialized) {
                                patternDatabase.recordDashDuration(pressDuration)
                            }
                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                    
                    updateInputDisplay()
                    
                    // 자동 문자 구분 인식
                    scheduler.schedule({
                        val now = System.currentTimeMillis()
                        if (now - lastInputTime > 1000) {
                            runOnUiThread {
                                if (pauseDuration > 0 && ::patternDatabase.isInitialized) {
                                    patternDatabase.recordCharSpaceDuration(pauseDuration)
                                }
                                currentInput.append(" ")
                                updateInputDisplay()
                            }
                        }
                    }, 1200, TimeUnit.MILLISECONDS)
                    
                    true
                }
                else -> false
            }
        }

        // 버튼 리스너 설정
        nextButton.setOnClickListener {
            checkCurrentWord()
        }

        skipButton.setOnClickListener {
            skipCurrentWord()
        }

        finishButton.setOnClickListener {
            finishTraining()
        }
    }

    private fun startTraining() {
        isTrainingActive = true
        currentWordIndex = 0
        currentRepetition = 0
        
        instructionText.text = "Let's learn your personal Morse code patterns!\nTouch and hold to enter dots and dashes."
        updateProgressDisplay()
        showCurrentWord()
    }

    private fun showCurrentWord() {
        if (currentWordIndex >= trainingWords.size) {
            completeTraining()
            return
        }

        val currentWord = trainingWords[currentWordIndex]
        val morseCode = morseConverter.textToMorse(currentWord)
        
        targetWordText.text = "Word: $currentWord"
        currentMorseText.text = "Morse: $morseCode"
        userInputText.text = ""
        
        // 현재 단어의 모스코드를 진동으로 시연
        demonstrateMorseCode(morseCode)
        
        // 입력 초기화
        currentInput.clear()
        inputTimings.clear()
        lastInputTime = 0
        
        updateProgressDisplay()
    }

    private fun demonstrateMorseCode(morseCode: String) {
        scheduler.schedule({
            runOnUiThread {
                Toast.makeText(this, "Listen to the pattern:", Toast.LENGTH_SHORT).show()
            }
            playMorseCodeVibration(morseCode)
        }, 500, TimeUnit.MILLISECONDS)
    }

    private fun playMorseCodeVibration(morseCode: String) {
        val vibrationPattern = mutableListOf<Long>()
        
        for (char in morseCode) {
            when (char) {
                '.' -> {
                    vibrationPattern.add(0L) // 대기 시간
                    vibrationPattern.add(150L) // dot 진동
                    vibrationPattern.add(100L) // 간격
                }
                '-' -> {
                    vibrationPattern.add(0L) // 대기 시간
                    vibrationPattern.add(400L) // dash 진동  
                    vibrationPattern.add(100L) // 간격
                }
                ' ' -> {
                    vibrationPattern.add(300L) // 문자 간 간격
                }
            }
        }
        
        if (vibrationPattern.isNotEmpty()) {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern.toLongArray(), -1))
        }
    }

    private fun updateInputDisplay() {
        currentMorseText.text = "Your input: ${currentInput.toString()}"
        
        // 입력된 모스코드를 텍스트로 변환하여 표시
        val convertedText = morseConverter.morseToText(currentInput.toString())
        userInputText.text = "Converted: $convertedText"
    }

    private fun checkCurrentWord() {
        val targetWord = trainingWords[currentWordIndex]
        val targetMorse = morseConverter.textToMorse(targetWord)
        val userMorse = currentInput.toString().trim()
        
        val similarity = morseConverter.calculateSimilarity(targetMorse, userMorse)
        
        if (similarity > 0.8f) {
            // 성공
            Toast.makeText(this, "Good! ${(similarity * 100).toInt()}% accuracy", Toast.LENGTH_SHORT).show()
            nextWord()
        } else {
            // 재시도 필요
            Toast.makeText(this, "Try again. Accuracy: ${(similarity * 100).toInt()}%", Toast.LENGTH_SHORT).show()
            currentRepetition++
            
            if (currentRepetition >= maxRepetitions) {
                // 최대 시도 횟수 초과 시 다음 단어로
                Toast.makeText(this, "Moving to next word...", Toast.LENGTH_SHORT).show()
                nextWord()
            } else {
                // 다시 시도
                showCurrentWord()
            }
        }
    }

    private fun skipCurrentWord() {
        nextWord()
    }

    private fun nextWord() {
        currentWordIndex++
        currentRepetition = 0
        showCurrentWord()
    }

    private fun completeTraining() {
        isTrainingActive = false
        
        // 트레이닝 완료 플래그 설정 (안전한 처리)
        if (::patternDatabase.isInitialized) {
            patternDatabase.setFirstRunCompleted()
        }
        
        instructionText.text = "Training completed!\nYour personal Morse code patterns have been learned."
        targetWordText.text = "Training Progress: 100%"
        currentMorseText.text = "You can now use the main app with personalized settings."
        userInputText.text = ""
        
        progressBar.progress = 100
        progressText.text = "100%"
        
        // 완료 버튼 표시
        nextButton.visibility = View.GONE
        skipButton.visibility = View.GONE
        finishButton.visibility = View.VISIBLE
        
        Toast.makeText(this, "Training completed! Personalized settings applied.", Toast.LENGTH_LONG).show()
    }

    private fun finishTraining() {
        // 메인 액티비티로 이동
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun updateProgressDisplay() {
        val totalWords = trainingWords.size
        val progress = ((currentWordIndex.toFloat() / totalWords) * 100).toInt()
        
        progressBar.progress = progress
        progressText.text = "$progress%"
        
        val progressInfo = "Word ${currentWordIndex + 1} of $totalWords"
        wordProgressText.text = progressInfo
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduler.shutdown()
    }

    override fun onBackPressed() {
        // 트레이닝 중 뒤로가기 방지
        if (isTrainingActive) {
            Toast.makeText(this, "Please complete the training or use the skip button.", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }
} 