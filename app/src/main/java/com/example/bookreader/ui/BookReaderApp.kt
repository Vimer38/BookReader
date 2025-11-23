package com.example.bookreader.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.example.bookreader.ui.navigation.BookReaderNavGraph

@Composable
fun BookReaderApp() {
    val navController = rememberNavController()
    BookReaderNavGraph(navController = navController)
}

