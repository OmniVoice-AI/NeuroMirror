package com.omnivoiceai.neuromirror.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.omnivoiceai.neuromirror.data.repositories.QuestionRepository
import com.omnivoiceai.neuromirror.ui.screens.auth.login.LoginScreen
import com.omnivoiceai.neuromirror.ui.screens.auth.login.LoginViewModel
import com.omnivoiceai.neuromirror.ui.screens.auth.register.RegisterScreen
import com.omnivoiceai.neuromirror.ui.screens.chat.ChatScreen
import com.omnivoiceai.neuromirror.ui.screens.chat.ChatViewModel
import com.omnivoiceai.neuromirror.ui.screens.home.HomeScreen
import com.omnivoiceai.neuromirror.ui.screens.loading.LoadingScreen
import com.omnivoiceai.neuromirror.ui.screens.note_detail.EmotionViewModel
import com.omnivoiceai.neuromirror.ui.screens.note_detail.NoteDetailsScreen
import com.omnivoiceai.neuromirror.ui.screens.notes.NotesViewModel
import com.omnivoiceai.neuromirror.ui.screens.profile.ProfileScreen
import com.omnivoiceai.neuromirror.ui.screens.profile.ProfileViewModel
import com.omnivoiceai.neuromirror.ui.screens.questions.NotesQuestionIntrospections
import com.omnivoiceai.neuromirror.ui.screens.questions.QuestionViewModel
import com.omnivoiceai.neuromirror.ui.screens.settings.SettingsScreen
import com.omnivoiceai.neuromirror.ui.screens.settings.theme.ThemeViewModel
import com.omnivoiceai.neuromirror.ui.screens.splash.SplashScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun NavGraph(
    navController: NavHostController,
    theme: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    val loginViewModel = koinViewModel<LoginViewModel>()
    val currentUser by loginViewModel.currentUser.collectAsStateWithLifecycle()

    val startDestination: NavigationRoute =
        if (currentUser != null) NavigationRoute.HomeScreen
        else NavigationRoute.SplashScreen

    val notesViewModel = koinViewModel<NotesViewModel>(parameters = { parametersOf("Neuro") })
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    val emotionViewModel = koinViewModel<EmotionViewModel>()
    val questionViewModel = koinViewModel<QuestionViewModel>(parameters = { parametersOf("Neuro") })
    val chatViewModel = koinViewModel<ChatViewModel>(parameters = { parametersOf("Neuro") })
    val questionRepository = koinInject<QuestionRepository>()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<NavigationRoute.SplashScreen> {
            SplashScreen(navController)
        }
        composable<NavigationRoute.LoginScreen> {
            LoginScreen(loginViewModel, navController)
        }
        composable<NavigationRoute.RegisterScreen> {
            RegisterScreen()
        }
        composable<NavigationRoute.HomeScreen> {
            HomeScreen(
                notesState = notesState,
                notesViewModel = notesViewModel,
                navController = navController
            )
        }
        composable<NavigationRoute.SettingsScreen> {
            SettingsScreen(loginViewModel, navController, theme)
        }
        composable<NavigationRoute.NoteDetailsScreen> { backStackEntry ->
            val route = backStackEntry.toRoute<NavigationRoute.NoteDetailsScreen>()
            NoteDetailsScreen(
                note = notesState.notes.first { it.id == route.id },
                emotionViewModel = emotionViewModel,
                questionViewModel = questionViewModel,
                navController = navController
            )
        }
        composable<NavigationRoute.ProfileScreen> {
            val profileVm = koinViewModel<ProfileViewModel>()
            ProfileScreen(profileVm, navController)
        }
        composable<NavigationRoute.LoadingScreen> { backStackEntry ->
            val route = backStackEntry.toRoute<NavigationRoute.LoadingScreen>()
            LoadingScreen()
        }
        composable<NavigationRoute.NoteQuestionsScreen> { backStackEntry ->
            val route = backStackEntry.toRoute<NavigationRoute.NoteQuestionsScreen>()
            val noteRepository = notesViewModel.getNoteRepository()
            NotesQuestionIntrospections(
                noteId = route.noteId,
                noteRepository = noteRepository,
                questionRepository = questionRepository,
                navController = navController
            )
        }
        composable<NavigationRoute.ChatScreen> { backStackEntry ->
            val route = backStackEntry.toRoute<NavigationRoute.ChatScreen>()
            ChatScreen(
                noteId = route.noteId,
                chatViewModel = chatViewModel,
                navController = navController
            )
        }
    }
}
