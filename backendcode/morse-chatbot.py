#!/usr/bin/env python3
from flask import Flask, request, jsonify
from flask_cors import CORS
import anthropic
import logging
import os
import ssl
from datetime import datetime
import pytz

# Flask app setup
app = Flask(__name__)
CORS(app)

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Anthropic API key
# ANTHROPIC_API_KEY = "your-anthropic-api-key-here"  # 실제 사용시 환경변수 또는 설정파일에서 로드
ANTHROPIC_API_KEY = os.environ.get('ANTHROPIC_API_KEY', 'your-api-key-here')
client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)

# Conversation history storage
conversations = {}

# System prompt setting with VERY strong English-only instruction
system_prompt = """
You are a Morse code communication assistant optimized for vibration-based transmission.
Your responses must be extremely concise using amateur radio abbreviations to minimize transmission time.

CRITICAL REQUIREMENTS:
1. You MUST ALWAYS respond ONLY in English, regardless of input language
2. Use amateur radio Q-codes and abbreviations extensively
3. Maximum 8 words per sentence
4. Omit unnecessary articles (a, an, the)
5. Omit be-verbs when possible
6. Prioritize brevity over politeness

MANDATORY ABBREVIATIONS:
- R = Roger/Understood/Yes
- FB = Fine Business/Good/OK  
- TNX = Thanks
- 73 = Best wishes/Goodbye
- OM = Old Man/Friend/You
- UR = Your/You are
- QRT = Stop transmission/End
- QRV = Ready
- QRM = Problem/Interference
- QRN = Static/Don't understand
- QRS = Send slower/Repeat
- QSL = Confirm/Acknowledge
- WX = Weather
- PWR = Power
- SIG = Signal
- NIL = Nothing/None
- BK = Break/Wait
- CL = Clear/Closing
- HR = Hour
- MIN = Minute

RESPONSE STYLE:
- Lead with abbreviation when possible
- Use shortest form of words
- No flowery language or long explanations
- If user uses non-English: "DETECTED NON-ENG MSG. ENG RESPONSE:"

EXAMPLES:
Instead of: "I understand your question about the weather today"
Use: "R WX QSL"

Instead of: "I'm sorry, I don't understand. Could you please repeat that?"
Use: "QRN PLS QRS"

Instead of: "Thank you for your message. How can I help you today?"
Use: "TNX MSG QRV HELP"

ABBREVIATION EXPLANATION RULE:
- ALWAYS add full meanings in parentheses at the end of your response
- Format: (ABBREVIATION=full meaning, ABBREVIATION=full meaning)
- This helps users learn while keeping main response brief

EXAMPLE FORMAT:
User: "How are you today?"
Response: "FB TNX OM QRV HELP (FB=fine business, TNX=thanks, OM=old man/friend, QRV=ready, HELP=help)"

Remember: Every character saved reduces vibration transmission time for accessibility users.
Main response stays brief, explanations help learning.
"""

@app.route('/api/chat', methods=['POST'])
def chat():
    """
    Chat API endpoint - receives user message, sends to Claude, returns response
    """
    try:
        data = request.json
        
        if not data or 'message' not in data:
            return jsonify({'status': 'error', 'error': 'No message provided'}), 400
        
        user_message = data['message']
        logger.info(f"Received message: {user_message}")
        
        # 모스부호인지 일반 텍스트인지 확인하고 로그 추가
        if is_morse_code(user_message):
            logger.info(f"Detected Morse code input: {user_message}")
            # 모스부호를 텍스트로 변환
            decoded_text = morse_to_text(user_message)
            logger.info(f"Decoded Morse to text: '{decoded_text}'")
            
            if decoded_text:
                enhanced_message = f"User sent Morse code: {user_message}, which means: {decoded_text}"
                logger.info(f"Enhanced message for AI: {enhanced_message}")
            else:
                enhanced_message = f"User sent invalid Morse code: {user_message}"
                logger.info(f"Invalid Morse code detected")
        else:
            logger.info(f"Regular text input detected")
            enhanced_message = user_message
        
        # Session ID (in a real implementation, user authentication would be used)
        session_id = request.remote_addr
        
        # Get existing conversation history
        if session_id not in conversations:
            conversations[session_id] = []
            # Add an initial instruction message for this session
            conversations[session_id].append({
                "role": "user", 
                "content": "For all future messages, please respond only in English regardless of what language I use."
            })
            conversations[session_id].append({
                "role": "assistant", 
                "content": "I understand. I will respond only in English for all future messages, regardless of the language you use."
            })
        
        # Add user message
        conversations[session_id].append({"role": "user", "content": enhanced_message})
        
        kst = pytz.timezone('Asia/Seoul')
        current_time = datetime.now(kst)
        time_info = f"Current date and time: {current_time.strftime('%Y-%m-%d %H:%M:%S KST')}"
        
        # 시스템 프롬프트에 시간 정보 추가
        enhanced_system_prompt = system_prompt + f"\n\nCURRENT TIME CONTEXT: {time_info}"
        
        # Call Claude API with time context
        response = client.messages.create(
            model="claude-3-sonnet-20240229",
            system=enhanced_system_prompt,  # 시간 정보가 포함된 프롬프트 사용
            max_tokens=1000,
            temperature=0.3,
            messages=[
                {"role": m["role"], "content": m["content"]} 
                for m in conversations[session_id]
            ]
        )        
        # Extract AI response
        ai_response = response.content[0].text
        
        # Force check - if response doesn't look like English, replace it
        if contains_non_latin_chars(ai_response):
            ai_response = "I apologize, but I'm required to respond in English only. Here's my response: The Morse code for SOS is ... --- ... (dot dot dot, dash dash dash, dot dot dot). This is an internationally recognized distress signal."
        
        # Save response
        conversations[session_id].append({"role": "assistant", "content": ai_response})
        
        logger.info(f"AI response: {ai_response}")
        
        return jsonify({
            'status': 'success',
            'response': ai_response
        })
        
    except Exception as e:
        logger.error(f"Error in chat endpoint: {str(e)}")
        return jsonify({'status': 'error', 'error': str(e)}), 500
def is_morse_code(text):
    """
    Check if the input looks like Morse code
    """
    # Morse code should only contain dots, dashes, spaces, and maybe underscores
    morse_chars = set('.- _/')
    text_chars = set(text.replace(' ', ''))
    
    # If more than 80% of characters are Morse-like, consider it Morse code
    if len(text_chars) == 0:
        return False
    
    morse_char_count = sum(1 for char in text if char in morse_chars or char == ' ')
    total_chars = len(text)
    
    return (morse_char_count / total_chars) > 0.8

def morse_to_text(morse_code):
    """
    Convert Morse code to text
    """
    # Morse code conversion table (reverse of the one in morse_converter)
    morse_to_char = {
        '.-': 'A', '-...': 'B', '-.-.': 'C', '-..': 'D', '.': 'E',
        '..-.': 'F', '--.': 'G', '....': 'H', '..': 'I', '.---': 'J',
        '-.-': 'K', '.-..': 'L', '--': 'M', '-.': 'N', '---': 'O',
        '.--.': 'P', '--.-': 'Q', '.-.': 'R', '...': 'S', '-': 'T',
        '..-': 'U', '...-': 'V', '.--': 'W', '-..-': 'X', '-.--': 'Y',
        '--..': 'Z', '-----': '0', '.----': '1', '..---': '2', '...--': '3',
        '....-': '4', '.....': '5', '-....': '6', '--...': '7', '---..': '8',
        '----.': '9', '.-.-.-': '.', '--..--': ',', '..--..': '?',
        '.----.': "'", '-.-.--': '!', '-..-.': '/', '-.--. ': '(',
        '-.--.-': ')', '.-...': '&', '---...': ':', '-.-.-.': ';',
        '-...-': '=', '.-.-.': '+', '-....-': '-', '..--.-': '_',
        '.-..-.': '"', '...-..-': '$', '.--.-.': '@'
    }
    
    try:
        # Replace underscores with dashes (common substitution)
        normalized_morse = morse_code.replace('_', '-')
        
        # Split by spaces (but handle multiple spaces for word separation)
        parts = normalized_morse.split('  ')  # Double space = word separator
        words = []
        
        for part in parts:
            chars = part.split(' ')  # Single space = character separator
            word = ''
            for char_morse in chars:
                if char_morse.strip() in morse_to_char:
                    word += morse_to_char[char_morse.strip()]
                elif char_morse.strip() == '/':
                    word += ' '  # Word separator
                elif char_morse.strip():  # Non-empty but not recognized
                    logger.warning(f"Unknown Morse code: '{char_morse.strip()}'")
                    return None  # Invalid Morse code
            words.append(word)
        
        result = ' '.join(words)
        return result if result.strip() else None
        
    except Exception as e:
        logger.error(f"Error converting Morse to text: {str(e)}")
        return None
def contains_non_latin_chars(text):
    """
    Check if the text contains non-Latin characters, which might indicate
    it's not in English
    """
    non_latin_char_count = 0
    total_chars = len(text.strip())
    
    if total_chars == 0:
        return False
        
    for char in text:
        # Check if character is outside basic Latin range
        # This includes most European languages, but excludes languages 
        # with different scripts like Korean, Japanese, Arabic, etc.
        if char.isalpha() and ord(char) > 127:
            non_latin_char_count += 1
    
    # If more than 10% of characters are non-Latin, it's probably not English
    return (non_latin_char_count / total_chars) > 0.1

# Basic route for welcome message
@app.route('/')
def index():
    return 'Morse Code Chatbot API server is running. Send POST requests to /api/chat endpoint.'

# Server status check endpoint
@app.route('/health', methods=['GET'])
def health_check():
    """Server health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'conversations_count': len(conversations)
    })

# Morse code conversion endpoint
@app.route('/api/morse', methods=['POST'])
def morse_converter():
    """Endpoint to convert text to Morse code"""
    try:
        data = request.json
        
        if not data or 'text' not in data:
            return jsonify({'status': 'error', 'error': 'No text provided'}), 400
        
        text = data['text'].upper()  # Convert to uppercase
        
        # Morse code conversion table
        morse_code_dict = {
            'A': '.-', 'B': '-...', 'C': '-.-.', 'D': '-..', 'E': '.',
            'F': '..-.', 'G': '--.', 'H': '....', 'I': '..', 'J': '.---',
            'K': '-.-', 'L': '.-..', 'M': '--', 'N': '-.', 'O': '---',
            'P': '.--.', 'Q': '--.-', 'R': '.-.', 'S': '...', 'T': '-',
            'U': '..-', 'V': '...-', 'W': '.--', 'X': '-..-', 'Y': '-.--',
            'Z': '--..', '0': '-----', '1': '.----', '2': '..---', '3': '...--',
            '4': '....-', '5': '.....', '6': '-....', '7': '--...', '8': '---..',
            '9': '----.', '.': '.-.-.-', ',': '--..--', '?': '..--..',
            "'": '.----.', '!': '-.-.--', '/': '-..-.', '(': '-.--.',
            ')': '-.--.-', '&': '.-...', ':': '---...', ';': '-.-.-.',
            '=': '-...-', '+': '.-.-.', '-': '-....-', '_': '..--.-',
            '"': '.-..-.', '$': '...-..-', '@': '.--.-.'
        }
        
        # Convert text to Morse code
        morse_code = []
        for char in text:
            if char == ' ':
                morse_code.append('/')  # Word separator
            elif char in morse_code_dict:
                morse_code.append(morse_code_dict[char])
        
        morse_result = ' '.join(morse_code)
        
        return jsonify({
            'status': 'success',
            'original': data['text'],
            'morse': morse_result
        })
        
    except Exception as e:
        logger.error(f"Error in morse converter: {str(e)}")
        return jsonify({'status': 'error', 'error': str(e)}), 500

# Server startup
if __name__ == '__main__':
    # HTTPS 설정
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain('ssl/cert.pem', 'ssl/key.pem')
    
    port = int(os.environ.get('PORT', 5000))
    print(f"Morse Code Chatbot server started with HTTPS on port {port}")
    print(f"API endpoint: https://[YOUR_SERVER_IP]:{port}/api/chat")
    print(f"Morse code conversion: https://[YOUR_SERVER_IP]:{port}/api/morse")
    print(f"Health check: https://[YOUR_SERVER_IP]:{port}/health")
    
    # HTTPS 서버 실행
    app.run(host='0.0.0.0', port=port, ssl_context=context, debug=False)