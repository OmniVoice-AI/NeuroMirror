package com.omnivoiceai.neuromirror.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnivoiceai.neuromirror.data.database.note.EmotionDetected
import com.omnivoiceai.neuromirror.data.database.note.Note
import com.omnivoiceai.neuromirror.data.repositories.EmotionRepository
import com.omnivoiceai.neuromirror.data.repositories.IntrospectionRepository
import com.omnivoiceai.neuromirror.data.repositories.NoteRepository
import com.omnivoiceai.neuromirror.data.repositories.QuestionRepository
import com.omnivoiceai.neuromirror.utils.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NoteRepository,
    private val emotionRepository: EmotionRepository,
    private val introspectionRepository: IntrospectionRepository,
    private val questionRepository: QuestionRepository,
): ViewModel() {
    val state = repository.notes.map { NotesState(it) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = NotesState(emptyList())
    )
    
    // Getter per il repository
    fun getNoteRepository(): NoteRepository = repository

    val actions = object : NotesActions {

        override fun addNote(note: Note): Job = viewModelScope.launch {
            val emotionLabel = emotionRepository.classify(note.content)
            val emotion = EmotionDetected.valueOf(emotionLabel.name)

            val updatedNote = note.copy(emotionDetected = emotion)
            repository.upsert(updatedNote)
        }


        override fun removeNote(note: Note): Job = viewModelScope.launch {
            repository.delete(note)
        }

        override fun generateQuestions(note: Note): Job = viewModelScope.launch {
            try {
                // Use the new extension function for type-safe question generation
                val response = introspectionRepository.sendQuestion(
                    userMessage = note.content,
                    threadId = note.id.toString()
                )
                
                // Parse and save questions using the response data
                questionRepository.saveQuestions(response.questions, note.id)
                
                // Update the note to mark it as evaluated
                val updatedNote = note.copy(isEvaluated = true)
                repository.upsert(updatedNote)
            } catch (e: Exception) {
                Logger.error("Error generating questions", e)
            }
        }
    }
}