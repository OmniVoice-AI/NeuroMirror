package com.omnivoiceai.neuromirror

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.omnivoiceai.neuromirror.data.database.AppDatabase
import com.omnivoiceai.neuromirror.data.ml.EmotionModel
import com.omnivoiceai.neuromirror.data.remote.ChatService
import com.omnivoiceai.neuromirror.data.remote.createChatService
import com.omnivoiceai.neuromirror.data.repositories.*
import com.omnivoiceai.neuromirror.ui.screens.auth.login.LoginViewModel
import com.omnivoiceai.neuromirror.ui.screens.chat.ChatViewModel
import com.omnivoiceai.neuromirror.ui.screens.note_detail.EmotionViewModel
import com.omnivoiceai.neuromirror.ui.screens.notes.NotesViewModel
import com.omnivoiceai.neuromirror.ui.screens.profile.ProfileViewModel
import com.omnivoiceai.neuromirror.ui.screens.questions.QuestionViewModel
import com.omnivoiceai.neuromirror.ui.screens.settings.theme.ThemeViewModel
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val Context.dataStore by preferencesDataStore("theme")

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `question_answers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `question_id` INTEGER NOT NULL,
                `answer_text` TEXT,
                `selected_option_index` INTEGER,
                `selected_option_text` TEXT,
                `created_at` INTEGER NOT NULL
            )
        """
        )
    }
}

val appModule = module {
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 60 seconds
                connectTimeoutMillis = 30000 // 30 seconds
                socketTimeoutMillis = 60000  // 60 seconds
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                level = LogLevel.ALL
            }
        }
    }
    single<ChatService> {
        Ktorfit.Builder()
            .baseUrl("https://api.ai.digitalnext.business/")
            .httpClient(get<HttpClient>())
            .build()
            .createChatService()
    }

    single { get<Context>().dataStore }
    single<FirebaseAuth> { FirebaseAuth.getInstance() }
    single { ThemeRepository(get()) }
    viewModel { ThemeViewModel(get()) }
    viewModel { EmotionViewModel(get()) }
    viewModel { (modelName: String) -> NotesViewModel(get(), get(), get(named(modelName)), get()) }
    viewModel { (modelName: String) -> QuestionViewModel(get(), get(), get(named(modelName))) }
    viewModel { (modelName: String) -> ChatViewModel(get(named(modelName)), get(), get()) }
    single {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            "note-list"
        ).addMigrations(MIGRATION_1_2).build()
    }
    single { NoteRepository(get<AppDatabase>().noteDao()) }
    single { QuestionRepository(get<AppDatabase>().questionDao(), get<AppDatabase>().noteDao()) }
    single { ProfileRepository(get()) }
    single { AuthRepository(get(), get()) }

    viewModel { ProfileViewModel(get()) }
    single { EmotionModel(get()) }
    single { EmotionRepository(get()) }
    viewModel { LoginViewModel(get()) }

    factory(named("Neuro")) { IntrospectionNeuroImpl(get(), get()) } bind IntrospectionRepository::class
}