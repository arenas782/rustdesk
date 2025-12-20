package href.cleverty.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log

class PermissionRequestTransparentActivity: Activity() {
    private val logTag = "permissionRequest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(logTag, "onCreate PermissionRequestTransparentActivity: intent.action: ${intent.action}")

        when (intent.action) {
            ACT_REQUEST_MEDIA_PROJECTION -> {
                // Check if we're a system app with CAPTURE_VIDEO_OUTPUT permission
                if (hasSystemMediaProjectionPermission()) {
                    Log.d(logTag, "System app - bypassing MediaProjection consent")
                    launchServiceWithoutProjection()
                    finish()
                    return
                }

                val mediaProjectionManager =
                    getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                startActivityForResult(intent, REQ_REQUEST_MEDIA_PROJECTION)
            }
            else -> finish()
        }
    }

    /**
     * Check if app has system-level screen capture permissions
     * These permissions are only available to apps signed with platform keys
     */
    private fun hasSystemMediaProjectionPermission(): Boolean {
        return checkCallingOrSelfPermission("android.permission.CAPTURE_VIDEO_OUTPUT") == PackageManager.PERMISSION_GRANTED ||
               checkCallingOrSelfPermission("android.permission.READ_FRAME_BUFFER") == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launch service without MediaProjection intent - for system apps
     * The service will need to handle creating MediaProjection differently
     */
    private fun launchServiceWithoutProjection() {
        Log.d(logTag, "Launch MainService (system app mode)")
        val serviceIntent = Intent(this, MainService::class.java)
        serviceIntent.action = ACT_INIT_MEDIA_PROJECTION_AND_SERVICE
        serviceIntent.putExtra("system_app_mode", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                launchService(data)
            } else {
                setResult(RES_FAILED)
            }
        }

        finish()
    }

    private fun launchService(mediaProjectionResultIntent: Intent) {
        Log.d(logTag, "Launch MainService")
        val serviceIntent = Intent(this, MainService::class.java)
        serviceIntent.action = ACT_INIT_MEDIA_PROJECTION_AND_SERVICE
        serviceIntent.putExtra(EXT_MEDIA_PROJECTION_RES_INTENT, mediaProjectionResultIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

}