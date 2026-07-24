package com.pulsefusionnet.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    INSTALLING,
    UP_TO_DATE,
    ERROR
}

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String
)

/**
 * Handles In-App Update checking, downloading over network, and triggering automatic APK installation.
 */
object InAppUpdateManager {

    // Default update endpoint (GitHub Raw android_app/update.json + GitHub Releases API fallback)
    private const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/piyush7911/PulseFusionNet/main/android_app/update.json"
    private const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/piyush7911/PulseFusionNet/releases/latest"
    private const val CURRENT_VERSION_CODE = 5
    const val CURRENT_VERSION_NAME = "v1.4.0"

    /**
     * Checks remote GitHub repository for available releases or update.json.
     */
    suspend fun checkForUpdate(
        customUrl: String? = null
    ): Pair<UpdateStatus, UpdateInfo?> = withContext(Dispatchers.IO) {
        val targetUrl = customUrl ?: DEFAULT_UPDATE_URL
        try {
            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PulseFusionNet-Android")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                val latestVersionCode = json.optInt("versionCode", CURRENT_VERSION_CODE)
                val latestVersionName = json.optString("versionName", CURRENT_VERSION_NAME)
                val changelog = json.optString("changelog", "Engine improvements & accuracy updates.")
                val downloadUrl = json.optString(
                    "downloadUrl",
                    "https://github.com/piyush7911/PulseFusionNet/releases/latest/download/app-release.apk"
                )

                val updateInfo = UpdateInfo(latestVersionName, latestVersionCode, changelog, downloadUrl)

                if (latestVersionCode > CURRENT_VERSION_CODE) {
                    Pair(UpdateStatus.UPDATE_AVAILABLE, updateInfo)
                } else {
                    Pair(UpdateStatus.UP_TO_DATE, updateInfo)
                }
            } else {
                // Secondary Fallback: Query GitHub Releases API directly
                fetchFromGitHubReleasesApi()
            }
        } catch (e: Exception) {
            fetchFromGitHubReleasesApi()
        }
    }

    private fun fetchFromGitHubReleasesApi(): Pair<UpdateStatus, UpdateInfo?> {
        return try {
            val url = URL(GITHUB_RELEASES_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PulseFusionNet-Android")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                val tagName = json.optString("tag_name", CURRENT_VERSION_NAME)
                val changelog = json.optString("body", "GitHub Release update.")
                
                // Parse assets for .apk file
                var apkUrl = "https://github.com/piyush7911/PulseFusionNet/releases/latest/download/app-release.apk"
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", apkUrl)
                            break
                        }
                    }
                }

                val updateInfo = UpdateInfo(tagName, CURRENT_VERSION_CODE + 1, changelog, apkUrl)
                if (tagName != CURRENT_VERSION_NAME) {
                    Pair(UpdateStatus.UPDATE_AVAILABLE, updateInfo)
                } else {
                    Pair(UpdateStatus.UP_TO_DATE, updateInfo)
                }
            } else {
                Pair(UpdateStatus.UP_TO_DATE, null)
            }
        } catch (e: Exception) {
            Pair(UpdateStatus.UP_TO_DATE, null)
        }
    }

    /**
     * Downloads APK from network and launches automatic installation Intent.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                connect()
            }

            val fileLength = connection.contentLength
            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count.toLong()
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            onProgress(progress)
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            // Launch APK Install Intent
            installApk(context, apkFile)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches Android PackageInstaller Intent via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }
}
