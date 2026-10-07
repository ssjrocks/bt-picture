package com.spl1nt.snaplabel.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val tagName: String,
    val releaseName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Checks GitHub Releases for a newer build of this app and can download +
 * launch the installer for it. No server of our own, no embedded token — the
 * repo is public specifically so the unauthenticated Releases API works here.
 *
 * Versioning convention: each release is tagged "v<versionCode>" (e.g. "v1",
 * "v2", ...), matching [android.content.pm.PackageInfo.versionCode] /
 * `defaultConfig.versionCode` in build.gradle.kts — NOT semver. A release is
 * "newer" purely if its tag's number is greater than the installed app's
 * version code. Keep bumping versionCode and tagging releases `v<code>` for
 * this to keep working.
 */
object UpdateChecker {
    private const val OWNER = "ssjrocks"
    private const val REPO = "bt-picture"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@runCatching null // no releases published yet, or a transient API error — not fatal
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val remoteVersionCode = tag.removePrefix("v").toIntOrNull() ?: return@runCatching null
            if (remoteVersionCode <= currentVersionCode) return@runCatching null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            val url = apkUrl ?: return@runCatching null

            UpdateInfo(
                versionCode = remoteVersionCode,
                tagName = tag,
                releaseName = json.optString("name", tag),
                notes = json.optString("body", ""),
                apkUrl = url,
            )
        }
    }

    /** Kicks off the APK download via [DownloadManager]; the system shows its own progress notification. */
    fun startDownload(context: Context, info: UpdateInfo): Long {
        val request = DownloadManager.Request(info.apkUrl.toUri())
            .setTitle("SnapLabel ${info.tagName}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "update-${info.tagName}.apk")
            .setMimeType("application/vnd.android.package-archive")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** Polls [DownloadManager] until [downloadId] finishes; returns true if it completed successfully. */
    suspend fun awaitDownload(context: Context, downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var result = false
        var done = false
        while (!done) {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> { result = true; done = true }
                        DownloadManager.STATUS_FAILED -> { result = false; done = true }
                    }
                } else {
                    done = true
                }
            }
            if (!done) delay(400)
        }
        result
    }

    /** Launches the system package installer on an APK [DownloadManager] just finished fetching. */
    fun installDownloaded(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri = dm.getUriForDownloadedFile(downloadId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
