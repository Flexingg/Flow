package com.flow.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.data.cloud.FirestoreSyncManager
import com.flow.todo.data.health.HealthConnectManager
import com.flow.todo.data.local.FlowDatabase
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.data.system.ShizukuSystemColorManager
import com.flow.todo.data.usage.AppUsageManager
import com.flow.todo.data.usage.AppUsageShieldMonitor
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.ui.FlowAppShell
import com.flow.todo.ui.state.FlowViewModel
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.widget.FlowGlassWidgetProvider

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FlowViewModel
    private lateinit var shizukuManager: ShizukuSystemColorManager
    private lateinit var firestoreSyncManager: FirestoreSyncManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = FlowDatabase.getInstance(applicationContext)
        val repository = FlowRepository(database.flowDao(), applicationContext)
        val healthConnectManager = HealthConnectManager(applicationContext)
        shizukuManager = ShizukuSystemColorManager(applicationContext)
        val appUsageManager = AppUsageManager(applicationContext)
        val appUsageShieldMonitor = AppUsageShieldMonitor(applicationContext)
        firestoreSyncManager = FirestoreSyncManager(applicationContext, repository)

        viewModel = ViewModelProvider(
            this,
            FlowViewModel.provideFactory(
                repository = repository,
                healthConnectManager = healthConnectManager,
                shizukuManager = shizukuManager,
                appUsageManager = appUsageManager,
                appUsageShieldMonitor = appUsageShieldMonitor,
                firestoreSyncManager = firestoreSyncManager,
                appContext = applicationContext
            )
        )[FlowViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        handleIncomingIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val healthPermissionLauncher = rememberLauncherForActivityResult(
                contract = PermissionController.createRequestPermissionResultContract()
            ) {
                viewModel.refreshHealthAndSystemStatus()
            }

            val googleSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                viewModel.onGoogleSignInResult(result.data)
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

            val requestGoogleSignIn: () -> Unit = {
                runCatching {
                    viewModel.getGoogleSignInIntent()?.let { signInIntent ->
                        googleSignInLauncher.launch(signInIntent)
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
                    onRequestUsagePermission = requestUsagePermission,
                    onSignInWithGoogle = requestGoogleSignIn
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || !::viewModel.isInitialized) return
        when (intent.action) {
            FlowGlassWidgetProvider.ACTION_QUICK_CAPTURE -> {
                viewModel.openQuickCaptureWithDraft(initialTitle = "", autoStartVoice = false)
            }
            FlowGlassWidgetProvider.ACTION_VOICE_CAPTURE -> {
                viewModel.openQuickCaptureWithDraft(initialTitle = "", autoStartVoice = true)
            }
            FlowGlassWidgetProvider.ACTION_OPEN_GOALS -> {
                viewModel.selectTab(FlowTab.GOALS)
            }
            FlowGlassWidgetProvider.ACTION_OPEN_TODAY -> {
                viewModel.selectTab(FlowTab.TODAY_NEXT)
            }
            "com.google.android.gms.actions.CREATE_NOTE",
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: ""
                viewModel.openQuickCaptureWithDraft(
                    initialTitle = text.trim(),
                    autoStartVoice = false
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
        if (::firestoreSyncManager.isInitialized) {
            firestoreSyncManager.destroy()
        }
    }
}
