package href.cleverty.remote

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * Content Provider for enterprise integration
 * Allows other apps to query Cleverty Remote information
 *
 * Usage from other app:
 *   val uri = Uri.parse("content://href.cleverty.remote.provider/id")
 *   val cursor = contentResolver.query(uri, null, null, null, null)
 *   cursor?.use {
 *       if (it.moveToFirst()) {
 *           val rustdeskId = it.getString(it.getColumnIndex("id"))
 *       }
 *   }
 *
 * Or via ADB:
 *   adb shell content query --uri content://href.cleverty.remote.provider/id
 *   adb shell content query --uri content://href.cleverty.remote.provider/status
 */
class EnterpriseContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "EnterpriseProvider"
        const val AUTHORITY = "href.cleverty.remote.provider"

        private const val CODE_ID = 1
        private const val CODE_STATUS = 2
        private const val CODE_CONFIG = 3

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "id", CODE_ID)
            addURI(AUTHORITY, "status", CODE_STATUS)
            addURI(AUTHORITY, "config", CODE_CONFIG)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "EnterpriseContentProvider created")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_ID -> {
                val cursor = MatrixCursor(arrayOf("id", "timestamp"))
                val id = getCleverty RemoteId()
                cursor.addRow(arrayOf(id, System.currentTimeMillis()))
                cursor
            }
            CODE_STATUS -> {
                val cursor = MatrixCursor(arrayOf("service_running", "media_ready", "input_ready", "id"))
                val serviceRunning = MainService.isReady || MainService.isStart
                val mediaReady = MainService.isReady
                val inputReady = InputService.isOpen
                val id = getCleverty RemoteId()
                cursor.addRow(arrayOf(
                    if (serviceRunning) 1 else 0,
                    if (mediaReady) 1 else 0,
                    if (inputReady) 1 else 0,
                    id
                ))
                cursor
            }
            CODE_CONFIG -> {
                val cursor = MatrixCursor(arrayOf("key", "value"))
                cursor.addRow(arrayOf("rendezvous_server", ffi.FFI.getLocalOption("custom-rendezvous-server")))
                cursor.addRow(arrayOf("relay_server", ffi.FFI.getLocalOption("relay-server")))
                cursor.addRow(arrayOf("api_server", ffi.FFI.getLocalOption("api-server")))
                cursor.addRow(arrayOf("id", getCleverty RemoteId()))
                cursor
            }
            else -> null
        }
    }

    private fun getCleverty RemoteId(): String {
        return EnterpriseConfig.getCleverty RemoteId()
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.id"
            CODE_STATUS -> "vnd.android.cursor.item/vnd.$AUTHORITY.status"
            CODE_CONFIG -> "vnd.android.cursor.dir/vnd.$AUTHORITY.config"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
