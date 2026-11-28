package org.onedroid.radiowave

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.json.JSONObject
import org.onedroid.radiowave.app.App
import org.onedroid.radiowave.app.utils.setActivityProvider
import java.net.HttpURLConnection
import java.net.URL

private data class AvailableUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

class MainActivity : ComponentActivity() {
    private var availableUpdate by mutableStateOf<AvailableUpdate?>(null)
    private var isDownloading by mutableStateOf(false)
    private var downloadMessage by mutableStateOf<String?>(null)
    private var activeDownloadId: Long? = null
    private var waitingForInstallPermission = false
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId != activeDownloadId) return

            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val cursor = manager.query(DownloadManager.Query().setFilterById(completedId))
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        isDownloading = false
                        downloadMessage = null
                        manager.getUriForDownloadedFile(completedId)?.let(::installDownloadedApk)
                            ?: run { downloadMessage = "تعذر فتح ملف التحديث بعد تنزيله." }
                    } else {
                        isDownloading = false
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        downloadMessage = "فشل تنزيل التحديث (رمز $reason). حاول مرة أخرى."
                    }
                } else {
                    isDownloading = false
                    downloadMessage = "تعذر التحقق من ملف التحديث. حاول مرة أخرى."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setActivityProvider { this }
        installSplashScreen()

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
        receiverRegistered = true

        setContent {
            enableEdgeToEdge(
                SystemBarStyle.dark(MaterialTheme.colorScheme.onSurface.toArgb()),
                SystemBarStyle.dark(MaterialTheme.colorScheme.onSurface.toArgb()),
            )
            App()

            val update = availableUpdate
            if (update != null) {
                AlertDialog(
                    onDismissRequest = { if (!isDownloading) availableUpdate = null },
                    title = { Text("يتوفر تحديث جديد لـ ME-Radio") },
                    text = {
                        Column {
                            Text("الإصدار " + update.versionName + " أصبح متاحًا.")
                            if (update.releaseNotes.isNotBlank()) {
                                Text(update.releaseNotes, modifier = Modifier.padding(top = 8.dp))
                            }
                            if (isDownloading) {
                                Text("جاري تنزيل التحديث…", modifier = Modifier.padding(top = 12.dp))
                            }
                            downloadMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { startUpdateDownload(update) }, enabled = !isDownloading) {
                            Text(if (isDownloading) "جاري التنزيل…" else "تحديث الآن")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { availableUpdate = null }, enabled = !isDownloading) {
                            Text("لاحقًا")
                        }
                    }
                )
            }
        }

        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            waitingForInstallPermission = false
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            activeDownloadId?.let { id ->
                manager.getUriForDownloadedFile(id)?.let(::launchPackageInstaller)
            }
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(downloadReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun checkForUpdate() {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ME-Radio-Android")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@Thread

                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latestCode = release.optString("tag_name").removePrefix("v").toLongOrNull()
                    ?: return@Thread
                val installedCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
                if (latestCode <= installedCode) return@Thread

                val assets = release.optJSONArray("assets") ?: return@Thread
                var downloadUrl: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name") == APK_ASSET_NAME) {
                        downloadUrl = asset.optString("browser_download_url")
                            .takeIf { it.startsWith("https://") }
                        break
                    }
                }
                val updateUrl = downloadUrl ?: return@Thread
                val notes = release.optString("body")
                    .substringAfter("Release notes:", release.optString("body"))
                    .trim()
                val update = AvailableUpdate(
                    versionName = release.optString("name").ifBlank { "v" + latestCode },
                    releaseNotes = notes,
                    downloadUrl = updateUrl
                )
                runOnUiThread {
                    availableUpdate = update
                    downloadMessage = null
                }
            } catch (_: Exception) {
                // Update checks are optional: never block app startup or normal use.
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun startUpdateDownload(update: AvailableUpdate) {
        if (isDownloading) return
        try {
            val apkFile = java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_ASSET_NAME)
            if (apkFile.exists()) apkFile.delete()
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("تحديث ME-Radio")
                .setDescription("جاري تنزيل الإصدار " + update.versionName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, APK_ASSET_NAME)
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            activeDownloadId = manager.enqueue(request)
            isDownloading = true
            downloadMessage = null
        } catch (_: Exception) {
            isDownloading = false
            downloadMessage = "لم نتمكن من بدء التنزيل. حاول مرة أخرى."
        }
    }

    private fun installDownloadedApk(apkUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            waitingForInstallPermission = true
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            return
        }
        launchPackageInstaller(apkUri)
    }

    private fun launchPackageInstaller(apkUri: Uri) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (_: Exception) {
            downloadMessage = "تم تنزيل التحديث، لكن تعذر فتح شاشة التثبيت. افتح ملف ME-Radio.apk من التنزيلات."
        }
    }

    private companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/airline-tools/ME-Radio/releases/latest"
        const val APK_ASSET_NAME = "ME-Radio.apk"
    }
}