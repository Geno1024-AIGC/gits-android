package com.geno1024.ai.gits.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.geno1024.ai.gits.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands a downloaded build to the system to install.
 *
 * An app cannot replace itself, so this goes through the platform: either a package
 * installer session, or a copy in Downloads handed to the ordinary installer. Which one
 * is used depends on the platform version, because from Android 10 a session shows a
 * flow that a person is unlikely to read as "install the update you just downloaded".
 *
 * The copy of the build happens on [Dispatchers.IO] and can therefore take seconds, which
 * is why this suspends rather than returning: a caller on the main thread would otherwise
 * freeze the frame the button was pressed in.
 */
object ApkInstaller {

    /**
     * @param onResult a message for the person, already resolved to display text.
     */
    suspend fun install(activity: Activity, apk: File, onResult: (String) -> Unit) {
        runCatching {
            if (canInstallPackages(activity)) {
                sessionInstall(activity, apk)
                onResult(activity.getString(R.string.update_install_handed_off))
            } else if (Build.VERSION.SDK_INT >= 29) {
                installViaDownloads(activity, apk, onResult)
            } else {
                openInstaller(activity, apk, onResult)
            }
        }.onFailure {
            Log.w(TAG, "could not start the install", it)
            onResult(
                activity.getString(R.string.update_install_not_started, it.message ?: UNKNOWN_REASON),
            )
        }
    }

    /**
     * Whether the user has already allowed this app to install packages.
     *
     * Asked before any work is done, because finding out afterwards would mean
     * downloading tens of megabytes only to discover it cannot be used.
     */
    fun canInstallPackages(activity: Activity): Boolean =
        activity.packageManager.canRequestPackageInstalls()

    private suspend fun sessionInstall(activity: Activity, apk: File) {
        withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val installer = activity.packageManager.packageInstaller
            val session = installer.createSession(params)
            installer.openSession(session).use {
                it.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    it.fsync(out)
                }
                it.commit(resultIntent(activity).intentSender)
            }
        }
    }

    /**
     * Puts the build in Downloads and opens it there.
     *
     * A copy in the user's Downloads folder is left behind on purpose: it is the only
     * one of these routes where the file remains reachable if the install is refused.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun installViaDownloads(activity: Activity, apk: File, onResult: (String) -> Unit) {
        // Resolved first, because startActivity for an APK is the one route that can
        // return normally and still show nothing: a device with no installer, or one
        // that declines this MIME type, leaves the tap looking like a dead button.
        val probe = Intent(Intent.ACTION_VIEW).apply {
            // The Downloads collection is named rather than inserted, so this can ask
            // the question without creating the file it is asking about.
            setDataAndType(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (probe.resolveActivity(activity.packageManager) == null) {
            onResult(activity.getString(R.string.update_install_no_installer))
            return
        }

        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "gits-${apk.nameWithoutExtension}.apk")
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
        }
        val target = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("Downloads refused the file")

        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(target)?.use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Downloads would not open the file for writing")
            }

            val open = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(target, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(open)
            onResult(activity.getString(R.string.update_install_saved_to_downloads))
        } catch (failure: Throwable) {
            // A file that could not be installed should not be left behind looking
            // like a build that could be.
            resolver.delete(target, null, null)
            throw failure
        }
    }

    private fun openInstaller(activity: Activity, apk: File, onResult: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installerUri(activity, apk), APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        onResult(activity.getString(R.string.update_install_confirm))
    }

    /**
     * The build as something another app is allowed to read.
     *
     * A file:// Uri throws [FileUriExposedException] on every release this app supports,
     * so the installer has to be handed a grant it was given the authority for.
     */
    private fun installerUri(context: Context, apk: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)

    private fun resultIntent(activity: Activity): PendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        Intent(InstallReceiver.ACTION).setPackage(activity.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val UNKNOWN_REASON = "unknown reason"
    private const val TAG = "ApkInstaller"
}
