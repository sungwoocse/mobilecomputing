package com.example.mobilecomputing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

/**
 * AI 모델과 통신하는 클라이언트 클래스
 * 서버에 메시지를 전송하고 응답을 받아옵니다.
 */
class AIModelClient(private val context: Context) {
    
    companion object {
        private const val TAG = "AIModelClient"
        private const val SERVER_URL = "https://your-cloud-server.com/api/chat" // 실제 서버 URL로 변경 필요
        private const val TIMEOUT_MILLIS = 10000 // 타임아웃 10초
    }
    
    // 백그라운드 스레드 풀
    private val executor: ThreadPoolExecutor = Executors.newFixedThreadPool(2) as ThreadPoolExecutor
    
    // UI 스레드 핸들러
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 오프라인 대응용 기본 응답
    private val fallbackResponses = arrayOf(
        "인터넷 연결이 없습니다. 연결 상태를 확인해주세요.",
        "현재 오프라인 상태입니다. 응답을 제공할 수 없습니다.",
        "네트워크 연결이 필요합니다. 다시 시도해주세요.",
        "서버에 연결할 수 없습니다. 인터넷 연결을 확인해주세요."
    )
    
    /**
     * 사용자 메시지 전송 및 응답 수신
     * @param message 사용자 메시지
     * @param callback 응답 콜백
     */
    fun sendMessage(message: String, callback: (String) -> Unit) {
        // 네트워크 연결 확인
        if (!isNetworkConnected()) {
            Log.e(TAG, "네트워크 연결 없음")
            callback(getFallbackResponse())
            return
        }
        
        // 실제 구현에서는 서버와 통신 구현
        // 현재는 목업 응답만 제공
        
        executor.execute {
            try {
                // 서버 통신 시뮬레이션 (실제 구현 필요)
                Thread.sleep(500) // 통신 지연 시뮬레이션
                
                // 실제 서버 구현 시 주석 해제하여 사용
                // val response = sendToServer(message)

                // 현재는 가상 응답 사용 (서버 연동 시 제거)
                val response = getMockResponse(message)
                
                // UI 스레드에서 콜백 실행
                mainHandler.post {
                    callback(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "통신 오류: ${e.message}")
                mainHandler.post {
                    callback("죄송합니다. 통신 중 오류가 발생했습니다. 다시 시도해주세요.")
                }
            }
        }
    }
    
    /**
     * 실제 서버 통신 구현 (현재는 미사용)
     */
    private fun sendToServer(message: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(SERVER_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            // 요청 데이터 구성
            val requestBody = JSONObject().apply {
                put("message", message)
            }.toString()
            
            // 데이터 전송
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
                os.flush()
            }
            
            // 응답 확인
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 응답 읽기
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                return jsonResponse.optString("response", "서버 응답이 올바르지 않습니다.")
            } else {
                Log.e(TAG, "서버 오류: $responseCode")
                return "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            }
        } catch (e: IOException) {
            Log.e(TAG, "통신 실패", e)
            return "네트워크 오류가 발생했습니다. 다시 시도해주세요."
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * 네트워크 연결 상태 확인
     */
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = 
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = 
                connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * 오프라인 상태용 기본 응답 제공
     */
    private fun getFallbackResponse(): String {
        return fallbackResponses.random()
    }
    
    /**
     * 목업 응답 생성 (실제 서버 연동 전까지 사용)
     */
    private fun getMockResponse(message: String): String {
        // 간단한 키워드 기반 응답 (서버 연동 전 테스트용)
        return when {
            message.contains("안녕") -> "안녕하세요! 무엇을 도와드릴까요?"
            message.contains("이름") -> "저는 모스 코드 챗봇입니다."
            message.contains("모스") -> "모스 부호는 짧은 신호와 긴 신호를 조합하여 문자를 표현하는 코드입니다."
            message.contains("도움") -> "저는 모스 부호를 통해 대화할 수 있습니다. 점(.)과 대시(-)로 메시지를 입력하면 됩니다."
            message.contains("기능") -> "저는 모스 부호 입력, 진동 출력, 텍스트 변환 등의 기능을 제공합니다."
            message.contains("사용") -> "화면을 탭하여 모스 부호를 입력하세요. 짧게 탭하면 점(.), 길게 누르면 대시(-)가 입력됩니다."
            else -> "입력하신 내용은 '$message'입니다. 더 자세한 질문을 해주시면 도움드리겠습니다."
        }
    }
} 