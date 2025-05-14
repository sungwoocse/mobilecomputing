package com.example.mobilecomputing

class MorseCodeConverter {
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
     * 모스 부호를 텍스트로 변환
     * @param morse 변환할 모스 부호 문자열
     * @return 변환된 텍스트
     */
    fun morseToText(morse: String): String {
        // 단어 단위로 분리 (2개 이상의 스페이스)
        val words = morse.trim().split("  ")
        
        return words.map { word ->
            // 문자 단위로 분리 (단일 스페이스)
            word.split(" ").mapNotNull { code ->
                if (code.isNotEmpty()) {
                    morseToChar[code]
                } else null
            }.joinToString("")
        }.joinToString(" ")
    }
} 