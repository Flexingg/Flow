package com.flow.todo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.data.health.HealthConnectManager
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.data.system.ShizukuSystemColorManager
import com.flow.todo.data.usage.AppUsageManager
import com.flow.todo.ui.FlowAppShell
import com.flow.todo.ui.state.FlowViewModel
import com.flow.todo.ui.theme.FluidTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FlowViewModel
    private lateinit var shizukuManager: ShizukuSystemColorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = FlowDatabase.getInstance(applicationContext)
        val repository = FlowRepository(database.flowDao(), applicationContext)
        val healthConnectManager = HealthConnectManager(applicationContext)
        shizukuManager = ShizukuSystemColorManager(applicationContext)
        val appUsageManager = AppUsageManager(applicationContext)

        viewModel = ViewModelProvider(
            this,
            FlowViewModel.provideFactory(
                repository = repository,
                healthConnectManager = healthConnectManager,
                shizukuManager = shizukuManager,
                appUsageManager = appUsageManager
            )
        )[FlowViewModel::class.java]

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val healthPermissionLauncher = rememberLauncherForActivityResult(
                contract = PermissionController.createRequestPermissionResultContract()
            ) {
                viewModel.refreshHealthAndSystemStatus()
            }

            val requestHealthPermissions: () -> Unit = {
                val permissions = viewModel.getAllHealthPermissions()
                val launched = runCatching {
                    healthPermissionLauncher.launch(permissions)
                    true
                }.getOrDefault(false)

                if (!launched) {
                    runCatching {
                        val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        startActivity(settingsIntent)
                    }.onFailure {
                        runCatching {
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.google.android.apps.healthdata")
                            )
                            startActivity(marketIntent)
                        }
                    }
                }
            }

            val requestUsagePermission: () -> Unit = {
                viewModel.refreshHealthAndSystemStatus()
                if (!uiState.appUsageSnapshot.hasUsagePermission) {
                    runCatching {
                        val usageIntent = Intent(
                            Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(usageIntent)
                    }.onFailure {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                }
            }

            FluidTheme(
                preferences = uiState.accessibility,
                dailyProgress = uiState.dailyProgress
            ) {
                FlowAppShell(
                    viewModel = viewModel,
                    onRequestHealthPermissions = requestHealthPermissions,
                    onRequestUsagePermission = requestUsagePermission
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshHealthAndSystemStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::shizukuManager.isInitialized) {
            shizukuManager.destroy()
        }
    }
}
