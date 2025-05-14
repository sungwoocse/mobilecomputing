1장: 표지
Morse Code Chatbot Application
Enhancing AI Accessibility for People with Visual and Hearing Impairments
Presented by: Sungwoo Choi(20191378), Yongseong Eom (20211185)
May 20 2025
2장: Project Motivation
In the AI era, we've experienced the benefits of AI tools as conversational partners and psychological counselors
However, people with visual and hearing impairments face significant barriers when trying to use text-based or voice-based chatbots
Research shows Morse code and Braille are primary communication methods for this community
While Braille is widely used, this project implements vibration-based Morse code as specialized Braille devices were not available
Morse code can be implemented through simple vibration patterns on standard smartphones, making it more accessible than Braille which requires specialized hardware
Our goal: Bridge the digital divide by creating a vibration-based Morse code interface for AI chatbot interaction
3장: Technologies Used
Text-to-Vibration Module
Converts text to international Morse code standard vibration patterns. Implements precise timing for dots, dashes, and spaces to ensure accurate communication.
Cloud-based Architecture
Server handles AI processing and Morse code conversion to minimize mobile device computational load. Client focuses on vibration output and user interaction.
AI Model Integration
Utilizes OpenAI GPT API or lightweight LLM models to generate contextually relevant responses. Optimized for concise answers suitable for Morse code transmission.
Signal Recognition
Always-on background service detects specific knock patterns or device movements using accelerometer and gyroscope sensors to activate the app without screen interaction.
System Architecture
[우측에 다이어그램이 표시됨: User → Mobile Client (Vibration Interface) → Cloud Server ↔ AI Model ↔ Morse Converter]
4장: Application Flow
App Launch
User knocks phone with specific pattern to activate app
AI Processing
Server processes query and generates response via LLM
User Feedback
User receives response via vibration patterns and can continue conversation
Question Input
User inputs Morse code via touch patterns
Morse Response
AI response converted to Morse vibration patterns
5장: Key Features
Tactile Morse Input
Intuitive touch interface enables users to input Morse code through tapping patterns, making communication accessible for those with visual and hearing impairments.
Precise Vibration Patterns
Advanced haptic feedback algorithms ensure clear distinction between dots and dashes in Morse code, with customizable vibration intensity and duration for individual preferences.
AI Response Optimization
AI responses are specially optimized for Morse code transmission, ensuring concise yet informative answers that minimize transmission time while maximizing content value.
6장: Implementation Challenges & Solutions
Vibration Pattern Recognition
Accurate recognition of Morse code input is challenging due to variations in tap pressure, timing, and device sensitivity.
Solution: Adaptive machine learning for pattern calibration, training mode, haptic feedback
Battery Consumption
Continuous background monitoring and vibration patterns output significantly drain battery life, limiting daily usability.
Solution: Intelligent power management, scheduled activation windows, optimized vibration patterns
Real-time Response
Traditional AI responses require complete processing before delivery, causing significant delays in Morse code communication which can disrupt conversation flow.
Solution: Token-by-token vibration output delivers responses faster, creating near real-time interaction experience
Response Length
AI models typically generate responses too verbose and complex to transmit efficiently via Morse code vibrations.
Solution: Concise response generation, automatic summarization, user-requested detail expansion
7장: Future Roadmap
Expected Benefits
Enhanced information accessibility for people with visual and hearing impairments
Providing opportunities for socially vulnerable groups to utilize AI technology
Accessible using only basic smartphone functions without the need for expensive specialized equipment
Potential for future expansion into Braille hardware integration and specialized AI assistants for people with visual and hearing impairments