package com.omnivoiceai.neuromirror.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnivoiceai.neuromirror.data.repositories.IntrospectionRepository
import com.omnivoiceai.neuromirror.data.repositories.NoteRepository
import com.omnivoiceai.neuromirror.data.repositories.QuestionRepository
import com.omnivoiceai.neuromirror.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val currentMessage: String = ""
)

interface ChatActions {
    fun initializeChat(context: Context, noteId: Int)
    fun sendMessage(context: Context, noteId: Int)
    fun updateCurrentMessage(message: String)
}

class ChatViewModel(
    private val introspectionRepository: IntrospectionRepository,
    private val noteRepository: NoteRepository,
    private val questionRepository: QuestionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    val actions = object : ChatActions {
        override fun initializeChat(context: Context, noteId: Int) {
            if (_state.value.isInitialized) return
            
            viewModelScope.launch {
                try {
                    _state.value = _state.value.copy(isLoading = true)
                    
                    val noteWithQuestions = noteRepository.getNoteWithQuestions(noteId)
                    val questionsWithAnswers = questionRepository.getQuestionsWithAnswersByNoteId(noteId)
                    
                    // Build initial message with note content, emotion, and answers
                    val initialMessage = buildInitialMessage(noteWithQuestions, questionsWithAnswers)
                    
                    // Send to backend directly without showing the configuration message
                    val response = introspectionRepository.sendMessage(
                        userMessage = initialMessage,
                        threadId = noteId.toString()
                    )
                    
                    // Add ONLY the assistant response to start the conversation
                    val assistantMessage = ChatMessage(
                        content = response.body,
                        isUser = false
                    )
                    
                    _state.value = _state.value.copy(
                        messages = listOf(assistantMessage),
                        isInitialized = true,
                        isLoading = false
                    )
                    
                } catch (e: Exception) {
                    Logger.error("Error initializing chat", e)
                    val errorMessage = ChatMessage(
                        content = "Sorry, there was an error starting the conversation.",
                        isUser = false
                    )
                    _state.value = _state.value.copy(
                        messages = listOf(errorMessage),
                        isInitialized = true,
                        isLoading = false
                    )
                }
            }
        }

        override fun sendMessage(context: Context, noteId: Int) {
            val currentState = _state.value
            val userMessage = currentState.currentMessage.trim()
            
            if (userMessage.isBlank() || currentState.isLoading) return
            
            // Add user message immediately
            val userChatMessage = ChatMessage(
                content = userMessage,
                isUser = true
            )
            
            _state.value = currentState.copy(
                messages = currentState.messages + userChatMessage,
                currentMessage = "",
                isLoading = true
            )
            
            // Send to backend
            viewModelScope.launch {
                try {
                    val response = introspectionRepository.sendMessage(
                        userMessage = userMessage,
                        threadId = noteId.toString()
                    )
                    
                    // Add assistant response
                    val assistantMessage = ChatMessage(
                        content = response.body,
                        isUser = false
                    )
                    
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantMessage,
                        isLoading = false
                    )
                    
                } catch (e: Exception) {
                    Logger.error("Error sending message", e)
                    val errorMessage = ChatMessage(
                        content = "Sorry, there was an error sending your message.",
                        isUser = false
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + errorMessage,
                        isLoading = false
                    )
                }
            }
        }

        override fun updateCurrentMessage(message: String) {
            _state.value = _state.value.copy(currentMessage = message)
        }
    }

    private fun buildInitialMessage(noteWithQuestions: com.omnivoiceai.neuromirror.data.database.note.NoteWithQuestions, questionsWithAnswers: List<com.omnivoiceai.neuromirror.data.database.question.QuestionWithAnswer>): String {
        val initialMessageBuilder = StringBuilder()
        initialMessageBuilder.append("Note content: ${noteWithQuestions.note.content}\n\n")
        
        // Add emotion detected
        noteWithQuestions.note.emotionDetected?.let { emotion ->
            initialMessageBuilder.append("Detected emotion: ${emotion.name}\n\n")
        }
        
        if (questionsWithAnswers.isNotEmpty()) {
            initialMessageBuilder.append("Questions and Answers:\n")
            questionsWithAnswers.forEach { qa ->
                qa.question.title?.let { title ->
                    initialMessageBuilder.append("\nQ: $title\n")
                    qa.answer?.let { answer ->
                        when {
                            !answer.answerText.isNullOrBlank() -> {
                                initialMessageBuilder.append("A: ${answer.answerText}\n")
                            }
                            !answer.selectedOptionText.isNullOrBlank() -> {
                                initialMessageBuilder.append("A: ${answer.selectedOptionText}\n")
                            }
                            else -> {
                                initialMessageBuilder.append("A: No answer provided\n")
                            }
                        }
                    } ?: run {
                        initialMessageBuilder.append("A: No answer provided\n")
                    }
                }
            }
        }
        
        return initialMessageBuilder.toString()
    }
} 