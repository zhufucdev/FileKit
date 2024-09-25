package io.github.vinceglb.filekit.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

public actual data class PlatformFile(
    val uri: Uri,
    private val context: Context,
) {
    public actual val name: String by lazy {
        context.getFileName(uri) ?: throw IllegalStateException("Failed to get file name")
    }

    // On some devices uri.path doesn't seem to work and instead this OpenableColumns.DISPLAY_NAME
    // monstrosity has to be used.
    public actual val path: String?
        get() = context.contentResolver.let {
            it.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        }

    public actual suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        context
            .contentResolver
            .openInputStream(uri)
            .use { stream -> stream?.readBytes() }
            ?: throw IllegalStateException("Failed to read file")
    }

    public actual fun getStream(): PlatformInputStream {
        return context
            .contentResolver
            .openInputStream(uri)?.let {
                PlatformInputStream(it)
            } ?: throw IllegalStateException("Failed to open stream")
    }

    public actual fun getSize(): Long? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)
            ?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndex(OpenableColumns.SIZE).let(cursor::getLong)
            }
    }.getOrNull()

    public actual fun supportsStreams(): Boolean = true

    private fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
        else -> uri.path?.let(::File)?.name
    }

    private fun Context.getContentFileName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                .let(cursor::getString)
        }
    }.getOrNull()
}

public actual data class PlatformDirectory(
    val uri: Uri,
) {
    public actual val path: String? =
        uri.path
}
