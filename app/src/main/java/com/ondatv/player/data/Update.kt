package com.ondatv.player.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the app looks for a newer version: a small JSON file, kept in the repository next to the
 * code. Change this one line to publish updates from somewhere else. A copy of the app built by
 * someone else must point to its own file: an APK signed with another key does not install over it.
 *
 * {"versionCode": 5, "versionName": "0.4.0", "apkUrl": "https://...apk", "notes": "O que mudou"}
 */
const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/huccyfreefire123-hash/MBTV/main/update.json"

/** The version published in [UPDATE_JSON_URL]. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/** Where a download stands; [fraction] is null until its size is known. */
data class DownloadState(val status: Int, val fraction: Float?)

/** Self-update without a store: reads the published version and downloads its APK. */
object Updates {
    const val APK_MIME = "application/vnd.android.package-archive"
    private const val APK_NAME = "MBTV-update.apk"

    /** The published version, or null when it cannot be read (no internet, bad file). Never throws. */
    fun latest(): UpdateInfo? = runCatching {
        val c = URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.useCaches = false
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            UpdateInfo(
                json.getInt("versionCode"), json.optString("versionName"),
                json.getString("apkUrl").trim(), json.optString("notes").trim(),
            )
        } finally {
            c.disconnect()
        }
    }.getOrNull()?.takeIf { it.apkUrl.startsWith("https://") || it.apkUrl.startsWith("http://") }

    /** The file the update is downloaded to, in the app's own folder (no storage permission needed). */
    fun apkFile(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, APK_NAME) }

    /** Version code of the APK in [file] when it is an APK of this app; null for anything else. */
    fun apkVersion(context: Context, file: File): Long? {
        if (!file.isFile) return null
        val info = runCatching { context.packageManager.getPackageArchiveInfo(file.path, 0) }.getOrNull() ?: return null
        if (info.packageName != context.packageName) return null
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    /** Starts the download with the system's DownloadManager, which also shows its own notification. */
    fun download(context: Context, update: UpdateInfo): Long {
        apkFile(context)?.delete()
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("MBTV " + update.versionName)
            .setDescription("Baixando atualização")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    fun cancel(context: Context, id: Long) {
        runCatching { context.getSystemService(DownloadManager::class.java).remove(id) }
    }

    /** Null when the system no longer knows the download (removed from its notification, for instance). */
    fun state(context: Context, id: Long): DownloadState? {
        val query = DownloadManager.Query().setFilterById(id)
        context.getSystemService(DownloadManager::class.java).query(query)?.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadState(status, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
        }
        return null
    }
}
