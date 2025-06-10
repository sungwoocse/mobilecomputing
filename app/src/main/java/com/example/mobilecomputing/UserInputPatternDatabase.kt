package com.example.mobilecomputing

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs

/**
 * 사용자의 모스코드 입력 패턴을 학습하고 관리하는 클래스
 */
class UserInputPatternDatabase(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_input_patterns", Context.MODE_PRIVATE)
    
    // 입력 패턴 데이터 클래스
    data class InputPattern(
        val dotDurations: MutableList<Long> = mutableListOf(),
        val dashDurations: MutableList<Long> = mutableListOf(),
        val charSpaceDurations: MutableList<Long> = mutableListOf(),
        val wordSpaceDurations: MutableList<Long> = mutableListOf()
    )
    
    private var currentPattern = InputPattern()
    
    /**
     * dot 입력 시간을 기록
     */
    fun recordDotDuration(duration: Long) {
        currentPattern.dotDurations.add(duration)
        savePattern()
    }
    
    /**
     * dash 입력 시간을 기록
     */
    fun recordDashDuration(duration: Long) {
        currentPattern.dashDurations.add(duration)
        savePattern()
    }
    
    /**
     * 문자 간 간격을 기록
     */
    fun recordCharSpaceDuration(duration: Long) {
        currentPattern.charSpaceDurations.add(duration)
        savePattern()
    }
    
    /**
     * 단어 간 간격을 기록
     */
    fun recordWordSpaceDuration(duration: Long) {
        currentPattern.wordSpaceDurations.add(duration)
        savePattern()
    }
    
    /**
     * 현재까지 학습된 패턴을 저장
     */
    private fun savePattern() {
        val editor = prefs.edit()
        
        // 각 패턴의 평균값들을 저장
        if (currentPattern.dotDurations.isNotEmpty()) {
            val avgDot = currentPattern.dotDurations.average().toLong()
            editor.putLong("avg_dot_duration", avgDot)
        }
        
        if (currentPattern.dashDurations.isNotEmpty()) {
            val avgDash = currentPattern.dashDurations.average().toLong()
            editor.putLong("avg_dash_duration", avgDash)
        }
        
        if (currentPattern.charSpaceDurations.isNotEmpty()) {
            val avgCharSpace = currentPattern.charSpaceDurations.average().toLong()
            editor.putLong("avg_char_space_duration", avgCharSpace)
        }
        
        if (currentPattern.wordSpaceDurations.isNotEmpty()) {
            val avgWordSpace = currentPattern.wordSpaceDurations.average().toLong()
            editor.putLong("avg_word_space_duration", avgWordSpace)
        }
        
        // 총 학습 횟수 저장
        editor.putInt("total_dot_count", currentPattern.dotDurations.size)
        editor.putInt("total_dash_count", currentPattern.dashDurations.size)
        editor.putInt("total_char_space_count", currentPattern.charSpaceDurations.size)
        editor.putInt("total_word_space_count", currentPattern.wordSpaceDurations.size)
        
        editor.apply()
    }
    
    /**
     * 저장된 패턴을 로드
     */
    private fun loadPattern() {
        // 저장된 데이터가 있는지 확인하고 로드
        val dotCount = prefs.getInt("total_dot_count", 0)
        val dashCount = prefs.getInt("total_dash_count", 0)
        
        if (dotCount > 0 && dashCount > 0) {
            // 기존 데이터가 있으면 평균값으로 초기화
            val avgDot = prefs.getLong("avg_dot_duration", 150)
            val avgDash = prefs.getLong("avg_dash_duration", 400)
            
            // 기존 패턴으로 초기화 (평균값 기준)
            repeat(dotCount.coerceAtMost(10)) { currentPattern.dotDurations.add(avgDot) }
            repeat(dashCount.coerceAtMost(10)) { currentPattern.dashDurations.add(avgDash) }
        }
    }
    
    /**
     * 입력 시간으로 dot/dash 판단
     */
    fun classifyInput(duration: Long): InputType {
        val avgDot = getAverageDotDuration()
        val avgDash = getAverageDashDuration()
        
        // 기본값이 없으면 고정 임계값 사용
        if (avgDot == 0L || avgDash == 0L) {
            return if (duration < 200) InputType.DOT else InputType.DASH
        }
        
        // 학습된 패턴으로 분류
        val threshold = (avgDot + avgDash) / 2
        return if (duration < threshold) InputType.DOT else InputType.DASH
    }
    
    /**
     * 간격으로 space 타입 판단
     */
    fun classifySpace(duration: Long): SpaceType {
        val avgCharSpace = getAverageCharSpaceDuration()
        val avgWordSpace = getAverageWordSpaceDuration()
        
        // 기본값이 없으면 고정 임계값 사용
        if (avgCharSpace == 0L || avgWordSpace == 0L) {
            return when {
                duration < 800 -> SpaceType.CHAR_SPACE
                duration < 1500 -> SpaceType.WORD_SPACE
                else -> SpaceType.WORD_SPACE
            }
        }
        
        // 학습된 패턴으로 분류
        val threshold = (avgCharSpace + avgWordSpace) / 2
        return if (duration < threshold) SpaceType.CHAR_SPACE else SpaceType.WORD_SPACE
    }
    
    /**
     * 평균 dot 입력 시간 반환
     */
    fun getAverageDotDuration(): Long {
        return prefs.getLong("avg_dot_duration", 0)
    }
    
    /**
     * 평균 dash 입력 시간 반환
     */
    fun getAverageDashDuration(): Long {
        return prefs.getLong("avg_dash_duration", 0)
    }
    
    /**
     * 평균 문자 간격 시간 반환
     */
    fun getAverageCharSpaceDuration(): Long {
        return prefs.getLong("avg_char_space_duration", 0)
    }
    
    /**
     * 평균 단어 간격 시간 반환
     */
    fun getAverageWordSpaceDuration(): Long {
        return prefs.getLong("avg_word_space_duration", 0)
    }
    
    /**
     * 트레이닝이 완료되었는지 확인
     */
    fun isTrainingCompleted(): Boolean {
        val dotCount = prefs.getInt("total_dot_count", 0)
        val dashCount = prefs.getInt("total_dash_count", 0)
        val charSpaceCount = prefs.getInt("total_char_space_count", 0)
        
        // 각각 최소 5번 이상 입력되었으면 트레이닝 완료로 간주
        return dotCount >= 5 && dashCount >= 5 && charSpaceCount >= 3
    }
    
    /**
     * 첫 실행 여부 확인
     */
    fun isFirstRun(): Boolean {
        return prefs.getBoolean("first_run", true)
    }
    
    /**
     * 첫 실행 플래그 설정
     */
    fun setFirstRunCompleted() {
        prefs.edit().putBoolean("first_run", false).apply()
    }
    
    /**
     * 트레이닝 데이터 리셋
     */
    fun resetTrainingData() {
        currentPattern = InputPattern()
        prefs.edit().clear().apply()
    }
    
    /**
     * 트레이닝 진척도 반환 (0-100)
     */
    fun getTrainingProgress(): Int {
        val dotCount = prefs.getInt("total_dot_count", 0)
        val dashCount = prefs.getInt("total_dash_count", 0)
        val charSpaceCount = prefs.getInt("total_char_space_count", 0)
        
        val dotProgress = (dotCount.coerceAtMost(5) * 20) // 최대 20점
        val dashProgress = (dashCount.coerceAtMost(5) * 20) // 최대 20점
        val spaceProgress = (charSpaceCount.coerceAtMost(3) * 20) // 최대 60점
        
        return dotProgress + dashProgress + spaceProgress
    }
    
    init {
        loadPattern()
    }
    
    enum class InputType {
        DOT, DASH
    }
    
    enum class SpaceType {
        CHAR_SPACE, WORD_SPACE
    }
} 