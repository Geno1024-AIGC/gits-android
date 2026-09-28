package g.gits.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Turns a folder the system picker returned into a path Git can be pointed at.
 *
 * The picker hands back a `content://` address, which is a way of asking a provider
 * for bytes, not a location. Git reads and writes real files and knows nothing about
 * content providers, so an address has to be resolved to the directory behind it before
 * it is any use here.
 *
 * A document id looks like `<volume>:<path within the volume>`. Primary storage is
 * `primary`; anything else is the volume's identifier, which is also how it is mounted.
 * Not every provider is a place on disk at all: a cloud or network-backed folder has no
 * path, and that is reported rather than guessed at, because a repository written
 * through a stream of provider calls would not be a repository Git could reopen.
 */
object DocumentTree {

    private const val TAG = "DocumentTree"

    /** Keeps read access to a picked folder across restarts, where the grant would lapse. */
    fun takePersistablePermission(context: Context, uri: Uri) {
        val permission = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, permission) }
            .onFailure { Log.w(TAG, "could not keep access to $uri", it) }
    }

    /**
     * The directory [uri] names, or null when it is not somewhere on this device.
     *
     * The result is checked rather than assumed: an id can name a folder that was
     * unmounted, or a provider that turns out to be remote, and a path that does not
     * exist is worse than none because everything downstream fails later and further
     * away.
     */
    fun directoryOf(uri: Uri): File? {
        val path = pathFor(documentIdOf(uri)) ?: return null
        val directory = File(path).absoluteFile
        if (!directory.isDirectory) return null
        // Resolved rather than taken at its word: /sdcard and the like are links, and a
        // path that still contains one is not what the recents list should remember.
        return runCatching { directory.canonicalFile }.getOrDefault(directory)
    }

    /**
     * The path a document id names, or null when the id names no place.
     *
     * Split out from [directoryOf] because the mapping is where the mistakes are, and
     * checking it needs a directory to exist only for reasons that have nothing to do
     * with the mapping.
     */
    fun pathFor(documentId: String?): String? {
        if (documentId == null) return null
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        val within = documentId.substringAfter(':', missingDelimiterValue = "")
        val root = if (volume.equals(PRIMARY, ignoreCase = true)) PRIMARY_ROOT else "$STORAGE_ROOT/$volume"
        return if (within.isEmpty()) root else "$root/$within"
    }

    /** The real path, or a sentence saying why there is not one. */
    fun requireDirectoryOf(uri: Uri): Result<File> {
        val directory = directoryOf(uri)
        if (directory != null) {
            return Result.success(directory)
        }
        val documentId = documentIdOf(uri)
        return Result.failure(
            IllegalArgumentException(
                if (documentId == null) {
                    "That is not a folder this system recognises."
                } else {
                    "\"$documentId\" is not a folder stored on this device. " +
                        "Pick one from the device's own storage; a folder that lives " +
                        "on a network or in the cloud has no path, and Git needs one."
                }
            )
        )
    }

    /**
     * The id behind a tree or a file.
     *
     * A folder from the tree picker carries its own id, and the tree form is tried
     * first because that is the address worth remembering.
     */
    private fun documentIdOf(uri: Uri): String? {
        val tree = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (tree != null) return tree
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    }

    /** Where the picker should be allowed to start. */
    fun initialUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:") }
                .getOrNull()
        } else {
            null
        }

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    private const val PRIMARY = "primary"
    private const val PRIMARY_ROOT = "/storage/emulated/0"
    private const val STORAGE_ROOT = "/storage"
}
