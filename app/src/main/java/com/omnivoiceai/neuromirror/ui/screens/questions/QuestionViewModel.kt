package com.omnivoiceai.neuromirror.ui.screens.questions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.omnivoiceai.neuromirror.data.database.note.Note
import com.omnivoiceai.neuromirror.data.database.note.NoteWithQuestions
import com.omnivoiceai.neuromirror.data.database.question.QuestionWithDetails
import com.omnivoiceai.neuromirror.data.repositories.IntrospectionRepository
import com.omnivoiceai.neuromirror.data.repositories.NoteRepository
import com.omnivoiceai.neuromirror.data.repositories.QuestionRepository
import com.omnivoiceai.neuromirror.ui.navigation.NavigationRoute
import com.omnivoiceai.neuromirror.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuestionViewModel(
    private val questionRepository: QuestionRepository,
    private val noteRepository: NoteRepository,
    private val introspectionRepository: IntrospectionRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _questionsWithDetails = MutableStateFlow<List<QuestionWithDetails>>(emptyList())
    val questionsWithDetails = _questionsWithDetails.asStateFlow()

    // Metodo per ottenere una nota con le sue domande
    suspend fun getNoteWithQuestions(noteId: Int): NoteWithQuestions {
        return noteRepository.getNoteWithQuestions(noteId)
    }
    
    // Metodo per ottenere i dettagli delle domande per una nota specifica
    suspend fun getQuestionsWithDetailsByNoteId(noteId: Int): List<QuestionWithDetails> {
        val questions = questionRepository.getQuestionsWithDetailsByNoteId(noteId)
        _questionsWithDetails.value = questions
        return questions
    }

    fun generateQuestions(context: Context, note: Note, navController: NavController) {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Verifica se la nota ha già delle domande
                val noteWithQuestions = noteRepository.getNoteWithQuestions(note.id)
                
                if (noteWithQuestions.questions.isNotEmpty()) {
                    // Se la nota ha già delle domande, naviga direttamente alla schermata delle domande
                    navController.navigate(NavigationRoute.NoteQuestionsScreen(note.id))
                    return@launch
                }
                
                // Se non ha domande, naviga alla schermata di caricamento
                navController.navigate(NavigationRoute.LoadingScreen(note.id))
                
                // Use the new extension function for type-safe question generation
                val response = introspectionRepository.sendQuestion(
                    userMessage = note.content,
                    threadId = note.id.toString()
                )
                
                // Parse and save questions using the response data
                questionRepository.saveQuestions(response.questions, note.id)
                
                // Update the note to mark it as evaluated
                val updatedNote = note.copy(isEvaluated = true)
                noteRepository.upsert(updatedNote)
                
                // Navigate to the questions screen
                navController.navigate(NavigationRoute.NoteQuestionsScreen(note.id)) {
                    // Remove the loading screen from the back stack
                    popUpTo(NavigationRoute.LoadingScreen(note.id)) { inclusive = true }
                }
            } catch (e: Exception) {
                Logger.error("Error generating questions", e)
                // Navigate back to note details screen on error
                navController.popBackStack()
            } finally {
                _isLoading.value = false
            }
        }
    }
} 