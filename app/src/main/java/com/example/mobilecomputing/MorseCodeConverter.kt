package com.example.mobilecomputing

class MorseCodeConverter(private val patternDatabase: UserInputPatternDatabase? = null) {
    // 영문자, 숫자에 대한 모스 부호 맵핑
    private val charToMorse = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..",
        'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
        'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
        'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----",
        '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '@' to ".--.-.", ' ' to " "
    )

    // 모스 부호에서 문자로의 맵핑 (역변환용)
    private val morseToChar = charToMorse.entries.associate { (k, v) -> v to k }

    /**
     * 텍스트를 모스 부호로 변환
     * @param text 변환할 텍스트
     * @return 모스 부호 문자열
     */
    fun textToMorse(text: String): String {
        return text.uppercase().mapNotNull { char ->
            charToMorse[char]
        }.joinToString(" ")
    }

    /**
     * 모스 부호를 텍스트로 변환 (개선된 스페이스 인식)
     * @param morse 변환할 모스 부호 문자열
     * @return 변환된 텍스트
     */
    fun morseToText(morse: String): String {
        if (morse.trim().isEmpty()) return ""
        
        // 연속된 스페이스를 정규화
        val normalizedMorse = morse.trim().replace(Regex(" {2,}"), "  ")
        
        // 단어 단위로 분리 (2개 이상의 스페이스)
        val words = normalizedMorse.split("  ")
        
        return words.map { word ->
            // 문자 단위로 분리 (단일 스페이스)
            word.trim().split(" ").mapNotNull { code ->
                if (code.isNotEmpty()) {
                    morseToChar[code]
                } else null
            }.joinToString("")
        }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * 실시간 입력을 모스코드로 변환 (타이밍 기반)
     * @param inputTimings 입력 타이밍 리스트 (press duration, pause duration 쌍)
     * @return 변환된 모스코드 문자열
     */
    fun timingsToMorse(inputTimings: List<Pair<Long, Long>>): String {
        if (inputTimings.isEmpty()) return ""
        
        val morseBuilder = StringBuilder()
        
        for (i in inputTimings.indices) {
            val (pressDuration, pauseDuration) = inputTimings[i]
            
            // dot 또는 dash 판단
            val symbol = if (patternDatabase != null) {
                when (patternDatabase.classifyInput(pressDuration)) {
                    UserInputPatternDatabase.InputType.DOT -> "."
                    UserInputPatternDatabase.InputType.DASH -> "-"
                }
            } else {
                // 기본 임계값 사용
                if (pressDuration < 200) "." else "-"
            }
            
            morseBuilder.append(symbol)
            
            // 마지막 입력이 아닌 경우 pause 분석
            if (i < inputTimings.size - 1) {
                val spaceType = if (patternDatabase != null) {
                    patternDatabase.classifySpace(pauseDuration)
                } else {
                    // 기본 임계값 사용
                    when {
                        pauseDuration < 800 -> UserInputPatternDatabase.SpaceType.CHAR_SPACE
                        else -> UserInputPatternDatabase.SpaceType.WORD_SPACE
                    }
                }
                
                when (spaceType) {
                    UserInputPatternDatabase.SpaceType.CHAR_SPACE -> morseBuilder.append(" ")
                    UserInputPatternDatabase.SpaceType.WORD_SPACE -> morseBuilder.append("  ")
                }
            }
        }
        
        return morseBuilder.toString()
    }

    /**
     * 모스코드 문자열의 유효성 검사
     * @param morse 검사할 모스코드 문자열
     * @return 유효한 모스코드인지 여부
     */
    fun isValidMorse(morse: String): Boolean {
        if (morse.trim().isEmpty()) return false
        
        // 허용된 문자만 포함하는지 확인 (., -, 공백)
        val validChars = setOf('.', '-', ' ')
        return morse.all { it in validChars }
    }

    /**
     * 두 모스코드 문자열의 유사도 계산 (Levenshtein Distance 기반)
     * @param morse1 첫 번째 모스코드
     * @param morse2 두 번째 모스코드
     * @return 유사도 (0.0 ~ 1.0)
     */
    fun calculateSimilarity(morse1: String, morse2: String): Float {
        if (morse1.isEmpty() && morse2.isEmpty()) return 1.0f
        if (morse1.isEmpty() || morse2.isEmpty()) return 0.0f
        
        val maxLength = maxOf(morse1.length, morse2.length)
        val distance = levenshteinDistance(morse1, morse2)
        
        return (maxLength - distance).toFloat() / maxLength
    }
    
    /**
     * Levenshtein Distance 계산
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // deletion
                    dp[i][j - 1] + 1,     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }

    /**
     * 문자별 모스코드 분해
     * @param text 분해할 텍스트
     * @return 문자별 모스코드 리스트
     */
    fun getCharacterMorseCodes(text: String): List<String> {
        return text.uppercase().mapNotNull { char ->
            charToMorse[char]
        }
    }

    /**
     * 특정 문자의 모스코드 반환
     * @param char 문자
     * @return 해당 문자의 모스코드 (없으면 null)
     */
    fun getCharacterMorse(char: Char): String? {
        return charToMorse[char.uppercaseChar()]
    }
} 