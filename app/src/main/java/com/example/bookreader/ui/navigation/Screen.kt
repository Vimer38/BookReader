package com.example.bookreader.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Auth : Screen("auth")
    data object Main : Screen("main")
    data object Reader : Screen("reader?bookId={bookId}&title={title}&author={author}&path={path}&format={format}") {
        fun createRoute(
            bookId: String,
            title: String,
            author: String,
            path: String,
            format: String
        ): String {
            return "reader?bookId=$bookId&title=${encode(title)}&author=${encode(author)}&path=${encode(path)}&format=$format"
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

