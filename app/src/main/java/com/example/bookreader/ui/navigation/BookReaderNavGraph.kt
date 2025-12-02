package com.example.bookreader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bookreader.ui.auth.AuthScreen
import com.example.bookreader.ui.auth.AuthViewModel
import com.example.bookreader.ui.auth.SplashScreen
import com.example.bookreader.ui.books.BooksViewModel
import com.example.bookreader.ui.books.MyBooksScreen
import com.example.bookreader.ui.profile.ProfileScreen
import com.example.bookreader.ui.profile.ProfileViewModel
import com.example.bookreader.ui.reader.ReaderScreen
import com.example.bookreader.ui.reader.ReaderViewModel
import com.example.bookreader.ui.todos.TodosScreen
import com.example.bookreader.ui.todos.TodosViewModel
import com.example.bookreader.ui.upload.UploadBookScreen
import com.example.bookreader.ui.upload.UploadViewModel
import com.google.firebase.auth.FirebaseAuth

private val mainDestinations = listOf(
    BottomDestination(
        route = "books",
        label = "Мои книги",
        icon = { Icons.Outlined.MenuBook }
    ),
    BottomDestination(
        route = "upload",
        label = "Загрузка",
        icon = { Icons.Outlined.CloudUpload }
    ),
    BottomDestination(
        route = "profile",
        label = "Профиль",
        icon = { Icons.Outlined.Person }
    ),
    BottomDestination(
        route = "todos",
        label = "Задачи",
        icon = { Icons.Outlined.ListAlt }
    )
)

@Composable
fun BookReaderNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = { isLoggedIn ->
                    navController.navigate(if (isLoggedIn) Screen.Main.route else Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Auth.route) {
            val viewModel: AuthViewModel = hiltViewModel()
            AuthScreen(
                viewModel = viewModel,
                onSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            MainShell(
                openReader = { bookId, title, author, path, format ->
                    navController.navigate(
                        Screen.Reader.createRoute(bookId, title, author, path, format)
                    )
                },
                requestLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { defaultValue = "" },
                navArgument("title") { defaultValue = "" },
                navArgument("author") { defaultValue = "" },
                navArgument("path") { defaultValue = "" },
                navArgument("format") { defaultValue = "TXT" }
            )
        ) {
            val viewModel: ReaderViewModel = hiltViewModel()
            ReaderScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    openReader: (bookId: String, title: String, author: String, path: String, format: String) -> Unit,
    requestLogout: () -> Unit
) {
    val innerNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: mainDestinations.first().route
            NavigationBar {
                mainDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) {
                                innerNavController.navigate(destination.route) {
                                    popUpTo(innerNavController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon(), contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors()
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = innerNavController,
                startDestination = mainDestinations.first().route
            ) {
                composable(mainDestinations[0].route) {
                    val viewModel: BooksViewModel = hiltViewModel()
                    MyBooksScreen(
                        viewModel = viewModel,
                        openBook = openReader
                    )
                }
                composable(mainDestinations[1].route) {
                    val uploadViewModel: UploadViewModel = hiltViewModel()
                    UploadBookScreen(viewModel = uploadViewModel)
                }
                composable(mainDestinations[2].route) {
                    val profileViewModel: ProfileViewModel = hiltViewModel()
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            requestLogout()
                        }
                    )
                }
                composable(mainDestinations[3].route) {
                    val todosViewModel: TodosViewModel = hiltViewModel()
                    TodosScreen(viewModel = todosViewModel)
                }
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: () -> androidx.compose.ui.graphics.vector.ImageVector
)

