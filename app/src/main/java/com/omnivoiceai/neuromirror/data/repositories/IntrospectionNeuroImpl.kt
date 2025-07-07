package com.omnivoiceai.neuromirror.data.repositories

import android.content.Context
import com.omnivoiceai.neuromirror.data.remote.*
import com.omnivoiceai.neuromirror.utils.Logger
import com.omnivoiceai.neuromirror.utils.encryptMessage
import kotlinx.serialization.json.Json

interface IntrospectionRepository {

    suspend fun sendMessage(
        threadId: String,
        userMessage: String,
    ): ChatIntrospectionResponse

    suspend fun sendQuestion(
        userMessage: String,
        threadId: String,
    ): QuestionIntrospectionResponse
}

class IntrospectionNeuroImpl(
    private val context: Context,
    private val api: ChatService
) : IntrospectionRepository {

    override suspend fun sendMessage(
        threadId: String,
        userMessage: String
    ): ChatIntrospectionResponse {
        return sendMessageInt(
            userMessage = userMessage,
            threadId = threadId,
            agentType = AgentType.ChatIntrospection,
        ) as ChatIntrospectionResponse
    }

    override suspend fun sendQuestion(
        userMessage: String,
        threadId: String
    ): QuestionIntrospectionResponse {
        return sendMessageInt(
            userMessage = userMessage,
            threadId = threadId,
            agentType = AgentType.QuestionIntrospection,
        ) as QuestionIntrospectionResponse
    }


    private suspend fun sendMessageInt(
        userMessage: String,
        threadId: String,
        agentType: AgentType,
    ): IntrospectionResponse {
        val publicKeyPath = "public.key"
        val plainText = userMessage
        val encryptedMessage = encryptMessage(context, plainText, publicKeyPath)

        val request = SendMessageRequest(
            messageContent = encryptedMessage,
            threadId = threadId,
            agentType = agentType
        )

        Logger.info("SendMessageRequest:\n$request")
        Logger.info("Serialized:\n${Json.encodeToString(SendMessageRequest.serializer(), request)}")

        return try {
            val response = api.sendMessage(request)

            val messageContent = response.assistantMessage.firstOrNull()?.text?.value
                ?: throw IllegalStateException("Empty response from server")

            @Suppress("UNCHECKED_CAST")
            when (agentType) {
                AgentType.QuestionIntrospection -> {
                    val questionResponse = QuestionIntrospectionResponse(
                        body = messageContent.body,
                        questions = messageContent.questions
                    )
                    questionResponse
                }
                AgentType.ChatIntrospection -> {
                    val chatResponse = ChatIntrospectionResponse(
                        body = messageContent.body,
                        sources = messageContent.sources
                    )
                    chatResponse
                }
            }
        } catch (e: Exception) {
            Logger.error("Error sending message", e)
            throw e
        }
    }

} 