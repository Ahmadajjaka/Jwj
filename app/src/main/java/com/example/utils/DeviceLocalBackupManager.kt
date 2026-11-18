package com.example.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.data.repository.AccountingEngine
import com.example.data.repository.AccountingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceBackupStatus(
    val internalSnapshotExists: Boolean = false,
    val internalSnapshotBytes: Long = 0L,
    val sharedDocumentsSaved: Boolean = false,
    val lastSavedAtMs: Long = 0L,
    val locationLabel: String = "",
    val lastTriggerReason: String = ""
)

object DeviceLocalBackupManager {
    private const val INTERNAL_DIR = "auto_backups"
    private const val SNAPSHOT_FILE_NAME = "mamo_device_snapshot.mamo.gz"
    private const val DOCUMENTS_SUBFOLDER = "MamoAccounting"

    fun internalSnapshotFile(context: Context): File {
        val dir = File(context.applicationContext.filesDir, INTERNAL_DIR).apply { mkdirs() }
        return File(dir, SNAPSHOT_FILE_NAME)
    }

    fun defaultLocationLabel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "ذاكرة التطبيق الدائمة + مجلد Documents/$DOCUMENTS_SUBFOLDER"
        } else {
            "ذاكرة التطبيق الدائمة + مجلد المستندات الخاص بالتطبيق"
        }
    }

    fun inspectStatus(
        context: Context,
        sharedDocumentsSaved: Boolean = false,
        lastTriggerReason: String = ""
    ): DeviceBackupStatus {
        val file = internalSnapshotFile(context)
        val exists = file.exists() && file.length() > 0L
        return DeviceBackupStatus(
            internalSnapshotExists = exists,
            internalSnapshotBytes = if (exists) file.length() else 0L,
            sharedDocumentsSaved = sharedDocumentsSaved,
            lastSavedAtMs = if (exists) file.lastModified() else 0L,
            locationLabel = defaultLocationLabel(),
            lastTriggerReason = lastTriggerReason
        )
    }

    suspend fun readLatestInternalSnapshotBytes(context: Context): ByteArray? = withContext(Dispatchers.IO) {
        val file = internalSnapshotFile(context)
        if (!file.exists() || file.length() <= 0L) return@withContext null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        if (AccountingEngine.isGzipBytes(bytes)) bytes else null
    }

    suspend fun persistInstantDeviceSnapshot(
        context: Context,
        repo: AccountingRepository,
        triggerReason: String = "حفظ تلقائي"
    ): DeviceBackupStatus = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        repo.checkpointDatabaseWal()

        val plainJson = repo.exportJsonBackup()
        val gzipBytes = AccountingEngine.compressGzip(plainJson.toByteArray(Charsets.UTF_8))

        val targetFile = internalSnapshotFile(appContext)
        val tempFile = File(targetFile.parentFile, "$SNAPSHOT_FILE_NAME.tmp")
        tempFile.writeBytes(gzipBytes)
        if (!tempFile.renameTo(targetFile)) {
            targetFile.writeBytes(gzipBytes)
            tempFile.delete()
        }

        val sharedOk = writeToDeviceDocumentsSafely(appContext, gzipBytes)
        val now = System.currentTimeMillis()
        targetFile.setLastModified(now)

        DeviceBackupStatus(
            internalSnapshotExists = targetFile.exists() && targetFile.length() > 0L,
            internalSnapshotBytes = targetFile.length(),
            sharedDocumentsSaved = sharedOk,
            lastSavedAtMs = now,
            locationLabel = defaultLocationLabel(),
            lastTriggerReason = triggerReason
        )
    }

    private fun writeToDeviceDocumentsSafely(context: Context, gzipBytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching { writeMediaStoreDocumentsQ(context, gzipBytes) }.getOrDefault(false)
        }
        return runCatching {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return false
            val folder = File(baseDir, DOCUMENTS_SUBFOLDER).apply { mkdirs() }
            val outFile = File(folder, SNAPSHOT_FILE_NAME)
            outFile.writeBytes(gzipBytes)
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeMediaStoreDocumentsQ(context: Context, gzipBytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = "${Environment.DIRECTORY_DOCUMENTS}/$DOCUMENTS_SUBFOLDER/"

        // Try updating an existing MediaStore file owned by this app install first
        val existingUri = runCatching {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(SNAPSHOT_FILE_NAME, "${Environment.DIRECTORY_DOCUMENTS}/$DOCUMENTS_SUBFOLDER%")
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    ContentUris.withAppendedId(collection, id)
                } else null
            }
        }.getOrNull()

        if (existingUri != null) {
            val updated = runCatching {
                resolver.openOutputStream(existingUri, "wt")?.use { out ->
                    out.write(gzipBytes)
                } != null
            }.getOrDefault(false)
            if (updated) return true
        }

        // Insert a fresh MediaStore record (zero permissions required on Android 10+)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, SNAPSHOT_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val insertedUri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(insertedUri)?.use { out ->
                out.write(gzipBytes)
            } ?: error("Failed to open MediaStore output stream")
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, done, null, null)
            true
        } catch (t: Throwable) {
            runCatching { resolver.delete(insertedUri, null, null) }
            false
        }
    }
}
