package com.flow.todo.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.flow.todo.domain.model.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.Locale
import kotlin.math.roundToInt

class ShizukuSystemColorManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _lastAppliedHex = MutableStateFlow<String?>(null)
    val lastAppliedHex: StateFlow<String?> = _lastAppliedHex.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatus()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            refreshStatus()
        }
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        }
        refreshStatus()
    }

    fun destroy() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        }
    }

    fun refreshStatus(): ShizukuStatus {
        val newStatus = when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED -> {
                ShizukuStatus.READY_SECURE_SETTINGS
            }
            runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> {
                val hasPermission = runCatching {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)

                if (hasPermission) {
                    // Attempt to auto-grant WRITE_SECURE_SETTINGS so direct Settings.Secure.putString becomes available
                    runShizukuShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_SECURE_SETTINGS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        ShizukuStatus.READY_SECURE_SETTINGS
                    } else {
                        ShizukuStatus.READY_SHIZUKU
                    }
                } else {
                    ShizukuStatus.PERMISSION_REQUIRED
                }
            }
            else -> {
                val isInstalled = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            SHIZUKU_PACKAGE_NAME,
                            PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                    }
                    true
                }.getOrDefault(false)

                if (isInstalled) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            }
        }
        _status.value = newStatus
        return newStatus
    }

    fun requestPermission() {
        refreshStatus()
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        }
    }

    private fun runShizukuShellCommand(command: String): Boolean {
        return runCatching {
            runBlocking(Dispatchers.IO) {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                val exitCode = process.waitFor()
                exitCode == 0 || process.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    suspend fun applyMomentumColorForProgress(progress: Float, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val quantizedProgress = if (force) {
                progress.coerceIn(0f, 1f)
            } else {
                (progress.coerceIn(0f, 1f) * 20).roundToInt() / 20f
            }

            val hex = computeMomentumHex(quantizedProgress)

            if (!force && hex == _lastAppliedHex.value) {
                return@withContext true
            }

            // Back up original theme overlay if not yet saved
            if (!prefs.contains(KEY_ORIGINAL_THEME)) {
                val currentSetting = runCatching {
                    Settings.Secure.getString(context.contentResolver, SETTINGS_KEY)
                }.getOrNull()
                prefs.edit().putString(KEY_ORIGINAL_THEME, currentSetting ?: "{}").apply()
            }

            val currentJsonStr = runCatching {
                Settings.Secure.getString(context.contentResolver, SETTINGS_KEY)
            }.getOrNull()

            val jsonObject = if (!currentJsonStr.isNullOrBlank()) {
                runCatching { JSONObject(currentJsonStr) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

            jsonObject.put("android.theme.customization.system_palette", hex)
            jsonObject.put("android.theme.customization.accent_color", hex)
            jsonObject.put("android.theme.customization.color_source", "preset")
            jsonObject.put("android.theme.customization.theme_style", "VIBRANT")
            jsonObject.put("_applied_timestamp", System.currentTimeMillis())

            val jsonStr = jsonObject.toString()

            var success = runCatching {
                Settings.Secure.putString(context.contentResolver, SETTINGS_KEY, jsonStr)
            }.getOrElse { error ->
                if (error is SecurityException) {
                    runShizukuShellCommand("settings put secure $SETTINGS_KEY '$jsonStr'")
                } else {
                    false
                }
            }

            if (!success) {
                success = runShizukuShellCommand("settings put secure $SETTINGS_KEY '$jsonStr'")
            }

            if (success) {
                _lastAppliedHex.value = hex
                refreshStatus()
                com.flow.todo.widget.FlowGlassWidgetProvider.requestWidgetUpdate(context)
                true
            } else {
                false
            }
        }

    suspend fun restoreOriginalSystemPalette(): Boolean = withContext(Dispatchers.IO) {
        val saved = prefs.getString(KEY_ORIGINAL_THEME, null)
        val restoreJsonStr = if (!saved.isNullOrBlank() && saved != "{}") {
            runCatching {
                val obj = JSONObject(saved)
                obj.put("_applied_timestamp", System.currentTimeMillis())
                obj.toString()
            }.getOrDefault(saved)
        } else {
            JSONObject().apply {
                put("android.theme.customization.color_source", "home_wallpaper")
                put("_applied_timestamp", System.currentTimeMillis())
            }.toString()
        }

        var success = runCatching {
            Settings.Secure.putString(context.contentResolver, SETTINGS_KEY, restoreJsonStr)
        }.getOrElse { error ->
            if (error is SecurityException) {
                runShizukuShellCommand("settings put secure $SETTINGS_KEY '$restoreJsonStr'")
            } else {
                false
            }
        }

        if (!success) {
            success = runShizukuShellCommand("settings put secure $SETTINGS_KEY '$restoreJsonStr'")
        }

        if (success) {
            _lastAppliedHex.value = null
            refreshStatus()
            com.flow.todo.widget.FlowGlassWidgetProvider.requestWidgetUpdate(context)
            true
        } else {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "shizuku_system_color_prefs"
        private const val KEY_ORIGINAL_THEME = "original_theme_overlay_json"
        private const val SHIZUKU_REQUEST_CODE = 9021
        private const val SETTINGS_KEY = "theme_customization_overlay_packages"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

        private val STOPS = listOf(
            Triple(0xFF, 0x3B, 0x5C), // Red 0% #FF3B5C
            Triple(0xAF, 0x52, 0xDE), // Purple 25% #AF52DE
            Triple(0x2F, 0x6B, 0xFF), // Blue 50% #2F6BFF
            Triple(0xFF, 0xB8, 0x00), // Yellow 75% #FFB800
            Triple(0x22, 0xB3, 0x5E)  // Green 100% #22B35E
        )

        fun computeMomentumHex(progress: Float): String {
            return com.flow.todo.ui.theme.MomentumSpectrum.seedHexForProgress(progress)
        }
    }
}
