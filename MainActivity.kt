package com.example.cacheclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.cacheclean.ui.MainScreen
import com.example.cacheclean.ui.MainViewModel
import com.example.cacheclean.ui.theme.CacheCleanTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CacheCleanTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Возврат из системных настроек: перепроверяем доступ и размеры кэша.
        viewModel.onResume()
    }
}
