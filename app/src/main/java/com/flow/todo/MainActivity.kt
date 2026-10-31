package com.flow.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.ui.FlowAppShell
import com.flow.todo.ui.state.FlowViewModel
import com.flow.todo.ui.theme.FluidTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = FlowDatabase.getInstance(applicationContext)
        val repository = FlowRepository(database.flowDao(), applicationContext)
        val viewModel = ViewModelProvider(
            this,
            FlowViewModel.provideFactory(repository)
        )[FlowViewModel::class.java]

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            FluidTheme(preferences = uiState.accessibility) {
                FlowAppShell(viewModel = viewModel)
            }
        }
    }
}
