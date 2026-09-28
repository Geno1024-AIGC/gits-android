package g.gits.android.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Hands a downloaded build to the system to install.
 *
 * An app cannot replace itself, so this goes through the platform: either a package
 * installer session, or a copy in Downloads handed to the ordinary installer. Which one
 * is used depends on the platform version, because from Android 10 a session shows a
 * flow that a person is unlikely to read as "install the update you just downloaded".
 */
object ApkInstaller {

    fun install(activity: Activity, apk: File, onResult: (String) -> Unit) {
        runCatching {
            if (canInstallPackages(activity)) {
                sessionInstall(activity, apk)
                onResult("The install was handed to the system; its result arrives as a notification.")
            } else if (Build.VERSION.SDK_INT >= 29) {
                installViaDownloads(activity, apk, onResult)
            } else {
                openInstaller(activity, apk, onResult)
            }
        }.onFailure {
            Log.w(TAG, "could not start the install", it)
            onResult("The install could not be started: ${it.message ?: "unknown reason"}.")
        }
    }

    /**
     * Whether the user has already allowed this app to install packages.
     *
     * Asked before any work is done, because finding out afterwards would mean
     * downloading tens of megabytes only to discover it cannot be used.
     */
    fun canInstallPackages(activity: Activity): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun sessionInstall(activity: Activity, apk: File) {
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

    /**
     * Puts the build in Downloads and opens it there.
     *
     * A copy in the user's Downloads folder is left behind on purpose: it is the only
     * one of these routes where the file remains reachable if the install is refused.
     */
    private fun installViaDownloads(activity: Activity, apk: File, onResult: (String) -> Unit) {
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
            resolver.openOutputStream(target)?.use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Downloads would not open the file for writing")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(target, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            onResult("Saved to Downloads; confirm the install in the window that opens.")
        } catch (failure: Throwable) {
            // A file that could not be installed should not be left behind looking
            // like a build that could be.
            resolver.delete(target, null, null)
            throw failure
        }
    }

    private fun openInstaller(activity: Activity, apk: File, onResult: (String) -> Unit) {
        val uri: Uri = Uri.fromFile(apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        onResult("Confirm the install in the window that opens.")
    }

    private fun resultIntent(activity: Activity): PendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        Intent(InstallReceiver.ACTION).setPackage(activity.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val TAG = "ApkInstaller"
}
