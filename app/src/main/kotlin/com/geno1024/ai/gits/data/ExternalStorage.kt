package com.geno1024.ai.gits.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Whether a folder outside this app's own storage can hold a repository.
 *
 * The folder picker hands back a `content://` address, and [DocumentTree] turns that into
 * a real path so Git can be pointed at it. That is only half of the bargain: the grant
 * behind the address covers bytes moved through a content provider, not ordinary file
 * calls, and from Android 11 on the system refuses those outside the app's own folders
 * unless it has all files access. Git makes ordinary file calls, so without the grant a
 * repository cannot be made there — and without this, it cannot be said either.
 */
object ExternalStorage {

    private const val TAG = "ExternalStorage"

    private const val PROBE_PREFIX = ".gits-write-probe-"

    /**
     * Whether the person has let this app write files it does not own.
     *
     * Git needs the whole path rather than a handle to one file, so the grant has to be
     * a real one: all files access from Android 11 on, and the storage permission, which
     * is the same thing under an older name, before that.
     */
    fun isPermitted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Whether this app can actually put a file in [directory].
     *
     * Asked rather than reasoned about, because scoped storage, a read-only mount and a
     * folder belonging to somebody else all pass a permission check and fail the call Git
     * would make. The existing repository is tested when there is one: that is where Git
     * writes, so it is the place whose answer matters.
     *
     * A short-lived file is written and taken back again. Leaving it would put litter in
     * a folder the person is about to make a repository of, and a file that could not be
     * created is exactly the failure being looked for.
     */
    fun isWritable(directory: File): Boolean {
        val host = when {
            File(directory, ".git").isDirectory -> File(directory, ".git")
            directory.isDirectory -> directory
            else -> directory.parentFile
        } ?: return false
        if (!host.isDirectory) return false

        val probe = File(host, PROBE_PREFIX + System.nanoTime())
        return try {
            probe.createNewFile()
        } finally {
            probe.delete()
        }
    }

    /**
     * Opens the page that can grant it, so the answer has somewhere to be given.
     *
     * Tried in order rather than probed for: asking a package manager which page exists
     * would have to name it first, and this app is not allowed to see that far. A page
     * that cannot be opened is logged rather than thrown, because the message that sent
     * the person here is already on screen.
     */
    fun openPermissionSettings(context: Context) {
        val pages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val packageUri = Uri.parse("package:${context.packageName}")
            listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        } else {
            listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }

        val opened = pages.any { page ->
            runCatching { context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) Log.w(TAG, "no settings page would open for $pages")
    }
}
