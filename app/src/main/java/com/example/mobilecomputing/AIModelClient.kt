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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * AI 모델과 통신하는 클라이언트 클래스
 * 서버에 메시지를 전송하고 응답을 받아옵니다.
 */
class AIModelClient(private val context: Context) {
    
    companion object {
        private const val TAG = "AIModelClient"
        private const val SERVER_URL = "https://3.36.80.121:5000/api/chat" // EC2 서버 주소 (HTTPS로 변경)
        private const val TIMEOUT_MILLIS = 10000 // 타임아웃 10초
    }
    
    // 백그라운드 스레드 풀
    private val executor: ThreadPoolExecutor = Executors.newFixedThreadPool(2) as ThreadPoolExecutor
    
    // UI 스레드 핸들러
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 오프라인 대응용 기본 응답
    private val fallbackResponses = arrayOf(
        "Internet connection not available. Please check your connection.",
        "You are currently offline. Cannot provide a response.",
        "Network connection is required. Please try again later.",
        "Cannot connect to server. Please check your internet connection."
    )
    
    init {
        // SSL 인증서 검증 우회 설정 (개발 환경에서만 사용)
        trustAllCertificates()
    }
    
    /**
     * 모든 SSL 인증서를 신뢰하도록 설정 (보안을 위해 실제 배포 환경에서는 제거 필요)
     */
    private fun trustAllCertificates() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            
            // 호스트명 검증 우회
            val allHostsValid = HostnameVerifier { _, _ -> true }
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
            
            Log.d(TAG, "SSL 인증서 검증 우회 설정 완료")
        } catch (e: Exception) {
            Log.e(TAG, "SSL 설정 오류: ${e.message}")
        }
    }
    
    /**
     * 사용자 메시지 전송 및 응답 수신
     * @param message 사용자 메시지
     * @param callback 응답 콜백
     */
    fun sendMessage(message: String, callback: (String) -> Unit) {
        // 네트워크 연결 확인
        if (!isNetworkConnected()) {
            Log.e(TAG, "No network connection")
            callback(getFallbackResponse())
            return
        }
        
        executor.execute {
            try {
                Log.d(TAG, "서버 통신 시작: $SERVER_URL")
                // 실제 서버 통신 구현
                val response = sendToServer(message)
                Log.d(TAG, "서버 응답 성공: ${response.take(50)}...")
                
                // UI 스레드에서 콜백 실행
                mainHandler.post {
                    callback(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Communication error: ${e.message}", e)
                mainHandler.post {
                    callback("Sorry, an error occurred while communicating with the server. Please try again.")
                }
            }
        }
    }
    
    /**
     * 실제 서버 통신 구현
     */
    private fun sendToServer(message: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(SERVER_URL)
            connection = url.openConnection() as HttpURLConnection
            
            if (connection is HttpsURLConnection) {
                Log.d(TAG, "HTTPS 연결 설정 중...")
                // HTTPS 설정이 적용되어 있는지 확인
                connection.sslSocketFactory
                connection.hostnameVerifier
            }
            
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            // 요청 데이터 구성
            val requestBody = JSONObject().apply {
                put("message", message)
            }.toString()
            
            Log.d(TAG, "요청 전송: $requestBody")
            
            // 데이터 전송
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
                os.flush()
            }
            
            // 응답 확인
            val responseCode = connection.responseCode
            Log.d(TAG, "응답 코드: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 응답 읽기
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                return jsonResponse.optString("response", "Server response is invalid.")
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error details"
                Log.e(TAG, "Server error: $responseCode, Details: $errorResponse")
                return "Server error occurred. Please try again later."
            }
        } catch (e: IOException) {
            Log.e(TAG, "Communication failed", e)
            return "Network error occurred. Please try again."
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
     * 목업 응답 생성 (서버 연동 실패 시 백업용으로 유지)
     */
    private fun getMockResponse(message: String): String {
        // 간단한 키워드 기반 응답 (서버 연동 전 테스트용)
        return when {
            message.contains("hello") -> "Hello! How can I help you with Morse code today?"
            message.contains("name") -> "I am the Morse Code Chatbot."
            message.contains("morse") -> "Morse code is a communication system that uses dots and dashes to represent letters and numbers."
            message.contains("help") -> "I can help you communicate using Morse code. Input with dots(.) and dashes(-) to form messages."
            message.contains("feature") -> "I provide Morse code input, vibration output, and text conversion features."
            message.contains("use") -> "Tap the screen to input Morse code. Short tap for dot(.), long press for dash(-)."
            else -> "You sent: '$message'. Please ask a more specific question about Morse code or how to use this app."
        }
    }
} 