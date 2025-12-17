package com.moodmelody.service

import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import org.springframework.stereotype.Service

@Service
class OpenAIService(private val openAiService: OpenAiService) {
    
    fun analyzeMood(text: String): String {
        val messages = listOf(
            ChatMessage(ChatMessageRole.SYSTEM.value(), 
                "너는 기분을 분석하고 그에 맞는 음악을 추천해주는 AI야. " +
                "사용자의 기분을 분석하고, 그에 어울리는 음악 장르나 분위기를 한 문장으로 알려줘."),
            ChatMessage(ChatMessageRole.USER.value(), text)
        )

        val completionRequest = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(messages)
            .maxTokens(100)
            .temperature(0.7)
            .build()

        return openAiService.createChatCompletion(completionRequest)
            .choices[0].message.content
    }

    fun generatePlaylistDescription(mood: String, tracks: List<String>): String {
        val messages = listOf(
            ChatMessage(ChatMessageRole.SYSTEM.value(), 
                "너는 플레이리스트 설명을 생성하는 AI야. " +
                "주어진 기분과 트랙 목록을 바탕으로 매력적인 플레이리스트 설명을 생성해줘."),
            ChatMessage(ChatMessageRole.USER.value(), 
                "기분: $mood\n트랙 목록: ${tracks.take(5).joinToString(", ")}...")
        )

        val completionRequest = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(messages)
            .maxTokens(150)
            .temperature(0.8)
            .build()

        return openAiService.createChatCompletion(completionRequest)
            .choices[0].message.content
    }
}
