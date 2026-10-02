package moe.shizuku.manager.utils

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.BuildConfig
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ApkUtils.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object UpdateHelper {
    private val app = ShizukuApplication.application
    private val appContext = ShizukuApplication.appContext

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val commit: Int = 0,
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(
                this,
                other,
                { it.major },
                { it.minor },
                { it.patch },
                { it.commit },
            )

        override fun toString(): String =
            if (commit == 0)  "$major.$minor.$patch" else "$major.$minor.$patch.r$commit"

        companion object {
            fun parse(tag: String): Version? {
                val regex = Regex("""(\d+)\.(\d+)\.(\d+)(?:\.r(\d+))?""")
                val match = regex.find(tag) ?: return null
                val (major, minor, patch, commit) = match.destructured
                return Version(
                    major.toInt(),
                    minor.toInt(),
                    patch.toInt(),
                    commit.toIntOrNull() ?: 0
                )
            }
        }
    }

    data class Release(
        val version: Version,
        val filename: String,
        val url: String,
        val digest: String
    )

    @Serializable
    data class GitHubRelease(
        val tag_name: String,
        val prerelease: Boolean,
        val target_commitish: String = "",
        val assets: List<GitHubAsset>
    )

    @Serializable
    data class GitHubAsset(
        val name: String,
        val browser_download_url: String,
        val digest: String
    )

    private lateinit var latestRelease: Release

    suspend fun checkAndInstallUpdates() {
        if (isUpdateAvailable()) {
            update()
        } else {
            Toast
                .makeText(
                    appContext,
                    appContext.getString(R.string.update_latest_installed),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    fun isCheckForUpdatesEnabled(): Boolean = ShizukuSettings.getUpdateMode() != ShizukuSettings.UpdateMode.OFF

    suspend fun isNewUpdateAvailable(): Boolean {
        return isUpdateAvailable()
    }

    suspend fun isUpdateAvailable(): Boolean {
        try {
            val latest = fetchLatestRelease().version ?: return false
            val current = Version.parse(getVersionName()) ?: return false
            return latest > current
        } catch (e: Exception) {
            Toast
                .makeText(
                    appContext,
                    appContext.getString(R.string.update_check_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            return false
        }
    }

    fun updateLastPromptedVersion() = ShizukuSettings.setLastPromptedVersion(latestRelease.version.toString())

    suspend fun update() {
        if (!::latestRelease.isInitialized && !isUpdateAvailable()) return

        Toast
            .makeText(
                appContext,
                appContext.getString(R.string.update_downloading),
                Toast.LENGTH_SHORT,
            ).show()

        val apk =
            latestRelease.download()?.run {
                val pm = appContext.packageManager
                val apkPackageName = pm.getPackageArchiveInfo(
                    this.path, 0
                )?.packageName
                if (app.packageName != apkPackageName) {
                    try {
                        android.util.Log.d("UpdateHelper", "Changing package name from $apkPackageName to ${app.packageName}")
                        changePackageName(app.packageName)
                    } catch (e: Exception) {
                        Toast
                            .makeText(
                                appContext,
                                appContext.getString(R.string.update_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        return@update
                    }
                } else {
                    this
                }
            }
        if (apk == null) {
            Toast
                .makeText(
                    appContext,
                    appContext.getString(R.string.update_download_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val chooser = Intent.createChooser(installIntent, "Choose installer")
        val pm = appContext.packageManager
        val resolveInfos = pm.queryIntentActivities(installIntent, 0)
        var universalInstaller: android.content.ComponentName? = null
        val excludedComponents = mutableListOf<android.content.ComponentName>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString().lowercase()
            if (label.contains("lucky patcher") || pkg.contains("lucky")) {
                excludedComponents.add(android.content.ComponentName(pkg, info.activityInfo.name))
            } else if (label.contains("universal installer") || pkg.contains("universalinstaller") || pkg.contains("universal")) {
                universalInstaller = android.content.ComponentName(pkg, info.activityInfo.name)
            }
        }
        if (universalInstaller != null) {
            installIntent.component = universalInstaller
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            appContext.startActivity(installIntent)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && excludedComponents.isNotEmpty()) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
            }
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            appContext.startActivity(chooser)
        }
    }

    private suspend fun fetchLatestRelease(): Release =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/repos/TheShadyRainbow4/ShizElite/releases"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Couldn't fetch releases")

            val releases = json.decodeFromString<List<GitHubRelease>>(body)
            val filtered =
                if (ShizukuSettings.getUpdateMode() == ShizukuSettings.UpdateMode.BETA) {
                    releases
                } else {
                    releases.filter { it.target_commitish == "main" || it.target_commitish == "master" }
                }

            filtered
                .mapNotNull { release ->
                    val version =
                        Version.parse(release.tag_name)
                            ?: return@mapNotNull null
                    val asset =
                        release.assets.firstOrNull { it.name.endsWith(".apk") }
                            ?: return@mapNotNull null

                    Release(
                        version = version,
                        filename = asset.name,
                        url = asset.browser_download_url,
                        digest = asset.digest
                    )
                }.maxByOrNull { it.version }
                ?.also { latestRelease = it }
                ?: throw Exception("No valid releases found")
        }

    private suspend fun Release.download(): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            val apkFile = File(appContext.cacheDir, filename)
            apkFile.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
            }

            val downloadedDigest = "sha256:" + apkFile.sha256()
            if (downloadedDigest != digest)
                throw SecurityException("Digest of downloaded file does not match the one reported by GitHub")

            apkFile
        }

    fun File.sha256(): String {
        val bytes = readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

}



