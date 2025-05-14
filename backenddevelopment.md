# 모스 부호 챗봇 백엔드 개발 문서

## 개요
이 문서는 모스 부호 챗봇 안드로이드 앱과 연동할 백엔드 시스템 개발에 관한 상세 정보를 제공합니다. 백엔드는 Python으로 개발되며, Claude 3.7 Sonnet 모델을 활용한 API 서버로 구성됩니다.

## 기술 스택
- **서버**: AWS EC2
- **언어**: Python 3.12
- **AI 모델**: Anthropic의 Claude 3.7 Sonnet
- **통신 프로토콜**: HTTP/HTTPS (REST API)
- **데이터베이스**: S3, DynamoDB

## 시스템 아키텍처
```
[Android App] <--> [REST API Server] <--> [Claude 3.7 API]
```

## API 엔드포인트 명세

프론트엔드 코드에 호환되도록 다음 API 엔드포인트를 구현해야 합니다:

### 1. `/api/chat` 엔드포인트
- **메소드**: POST
- **기능**: 사용자 메시지를 받아 Claude에 전달하고 응답 반환
- **요청 형식**:
  ```json
  {
    "message": "사용자가 입력한 텍스트"
  }
  ```
- **응답 형식**:
  ```json
  {
    "response": "AI의 응답 메시지",
    "status": "success"
  }
  ```
- **에러 응답**:
  ```json
  {
    "error": "에러 메시지",
    "status": "error"
  }
  ```


## 백엔드 개발 단계

### 1. 서버 설정
```python
# app.py (Flask 예시)
from flask import Flask, request, jsonify
from flask_cors import CORS
import os
import anthropic
import logging

app = Flask(__name__)
CORS(app)  # CORS 설정

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Anthropic API 키 설정
ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY")
client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)

```

### 2. 채팅 API 엔드포인트 구현
```python
@app.route('/api/chat', methods=['POST'])
def chat():
    try:
        data = request.json
        
        if not data or 'message' not in data:
            return jsonify({'status': 'error', 'error': 'No message provided'}), 400
        
        user_message = data['message']
        logger.info(f"Received message: {user_message}")
        
        # 세션 ID (실제 구현에서는 사용자 인증 정보를 활용)
        session_id = request.remote_addr
        
        # 기존 대화 이력 가져오기
        if session_id not in conversations:
            conversations[session_id] = []
        
        # 사용자 메시지 추가
        conversations[session_id].append({"role": "user", "content": user_message})
        
        # Claude API 호출
        response = client.messages.create(
            model="claude-3-sonnet-20240229",
            max_tokens=1000,
            messages=[
                {"role": m["role"], "content": m["content"]} 
                for m in conversations[session_id]
            ]
        )
        
        # AI 응답 추출
        ai_response = response.content[0].text
        
        # 응답 저장
        conversations[session_id].append({"role": "assistant", "content": ai_response})
        
        return jsonify({
            'status': 'success',
            'response': ai_response
        })
        
    except Exception as e:
        logger.error(f"Error in chat endpoint: {str(e)}")
        return jsonify({'status': 'error', 'error': str(e)}), 500
```

```

### 4. 서버 실행 코드
```python
if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False)
```

## 배포 및 설정 가이드

### EC2 설정
1. EC2 인스턴스 시작 (Amazon Linux 2 또는 Ubuntu 서버 권장)
2. 보안 그룹 설정: HTTP(80), HTTPS(443), SSH(22) 포트 열기
3. 도메인 연결 (선택사항)

### 파이썬 환경 설정
```bash
# 필요한 패키지 설치
sudo apt update
sudo apt install -y python3-pip python3-venv

# 필요한 라이브러리 설치
pip install flask flask-cors gunicorn anthropic requests
```

### 환경 변수 설정
```bash
# .env 파일 생성
echo "ANTHROPIC_API_KEY=your_api_key_here" > .env

# 환경 변수 로드 스크립트
echo 'set -a; source .env; set +a' > load_env.sh
chmod +x load_env.sh
```

### 서비스 설정 (SystemD)
```
[Unit]
Description=Morse Code Chatbot Backend
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/morse-chatbot
ExecStart=/home/ubuntu/morse-chatbot/venv/bin/gunicorn -w 4 -b 0.0.0.0:5000 app:app
Restart=always
StandardOutput=journal
StandardError=journal
EnvironmentFile=/home/ubuntu/morse-chatbot/.env

[Install]
WantedBy=multi-user.target
```



## Claude 3.7 Sonnet 사용 가이드

### 모델 프롬프트 최적화
Claude 3.7 Sonnet을 모스 부호 챗봇 목적에 맞게 최적화하기 위한 프롬프트 전략:

```python
system_prompt = """
You are a helpful assistant specialized in Morse code and assistive communication. 
Your responses should be clear, concise, and educational about Morse code when relevant.
When responding to users who may have visual or hearing impairments:
1. Keep responses brief and to the point
2. Use simple language
3. Explain Morse code concepts clearly when asked
4. Provide helpful information about assistive technologies when relevant

Respond in the same language the user is using.
"""

# API 호출시 시스템 프롬프트 추가
response = client.messages.create(
    model="claude-3-sonnet-20240229",
    system=system_prompt,
    max_tokens=1000,
    messages=[...]
)
```

## 안드로이드 앱 연동 정보

### AIModelClient.kt 수정 필요 사항
안드로이드 앱의 `AIModelClient.kt` 파일에서 다음 부분을 업데이트해야 합니다:

```kotlin
// 실제 배포된 서버 URL로 변경
private const val SERVER_URL = "https://your-domain.com/api/chat"  // EC2 주소 또는 도메인으로 변경

// 실제 서버 통신 구현 (getMockResponse 대신 사용)
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
            return jsonResponse.optString("response", "Server response is invalid.")
        } else {
            Log.e(TAG, "Server error: $responseCode")
            return "Server error occurred. Please try again later."
        }
    } catch (e: IOException) {
        Log.e(TAG, "Communication failed", e)
        return "Network error occurred. Please try again."
    } finally {
        connection?.disconnect()
    }
}
```

## 테스트 및 모니터링

### 서버 테스트
```bash
# 서버 작동 테스트
curl -X POST http://localhost:5000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello in Morse code"}'
```

### 로그 모니터링
```bash
# 서비스 로그 확인
sudo journalctl -u morse-chatbot.service -f
```

## 성능 최적화
- 비동기 요청 처리 (AsyncIO 또는 FastAPI 사용 고려)
