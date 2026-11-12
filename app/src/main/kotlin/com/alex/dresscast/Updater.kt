package com.alex.dresscast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val number: Int, val apkUrl: String, val notes: String)

/** Checks the GitHub Releases page for a newer Clymo and installs it. */
object Updater {
    private const val API = "https://api.github.com/repos/Vyto-Line/Clymo/releases/latest"

    private fun info(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, 0)

    /** The build number, e.g. 8 for version 2.0.8. */
    fun currentNumber(ctx: Context): Long = PackageInfoCompat.getLongVersionCode(info(ctx))
    fun currentName(ctx: Context): String = info(ctx).versionName ?: "?"

    /** The latest published release. Call from a background thread. */
    fun latest(): Release? {
        val con = URL(API).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 15000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "Clymo-Android")
        try {
            if (con.responseCode != 200) return null
            val o = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name")
            val assets = o.getJSONArray("assets")
            // The phone app; releases also carry Clymo-Watch.apk for Wear OS.
            val list = (0 until assets.length()).map { assets.getJSONObject(it) }
            val apk = list.firstOrNull { it.getString("name") == "Clymo.apk" }
                ?: list.firstOrNull { it.getString("name") == "DressCast.apk" }
                ?: list.firstOrNull { it.getString("name").endsWith(".apk") && !it.getString("name").contains("Watch", true) } ?: return null
            val number = tag.substringAfterLast('.').toIntOrNull() ?: return null
            return Release(tag.removePrefix("v"), number, apk.getString("browser_download_url"), o.optString("body", ""))
        } finally {
            con.disconnect()
        }
    }

    /** A release newer than the installed app, or null. */
    fun newer(ctx: Context): Release? = latest()?.takeIf { it.number > currentNumber(ctx) }

    /** Downloads the APK, reporting progress from 0 to 1. Call from a background thread. */
    fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Clymo-${r.version}.apk")
        var url = URL(r.apkUrl)
        var con: HttpURLConnection
        // Follow GitHub's redirect to its download server ourselves, to be safe.
        var hops = 0
        while (true) {
            con = url.openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 20000
            con.readTimeout = 30000
            con.setRequestProperty("User-Agent", "Clymo-Android")
            val code = con.responseCode
            if (code in 300..399 && hops < 5) {
                url = URL(url, con.getHeaderField("Location"))
                con.disconnect()
                hops++
                continue
            }
            if (code != 200) throw Exception("HTTP $code")
            break
        }
        try {
            val total = con.contentLengthLong
            con.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            con.disconnect()
        }
        return file
    }

    /** Android asks once whether Clymo may install apps (its own updates). */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /** Opens Android's install screen for the downloaded update. */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
