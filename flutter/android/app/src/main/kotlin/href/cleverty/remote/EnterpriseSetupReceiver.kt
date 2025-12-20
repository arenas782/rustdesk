package href.cleverty.remote

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity

/**
 * Enterprise Setup Receiver - handles unattended deployment
 *
 * Trigger via ADB or from device owner app:
 *   am broadcast -a href.cleverty.remote.ENTERPRISE_SETUP --receiver-foreground -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
 *
 * Or with specific actions:
 *   am broadcast -a href.cleverty.remote.ENABLE_ACCESSIBILITY -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
 *   am broadcast -a href.cleverty.remote.ENABLE_START_ON_BOOT -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
 *   am broadcast -a href.cleverty.remote.START_SERVICE -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
 *   am broadcast -a href.cleverty.remote.GET_RUSTDESK_ID -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
 */
class EnterpriseSetupReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EnterpriseSetup"

        const val ACTION_ENTERPRISE_SETUP = "href.cleverty.remote.ENTERPRISE_SETUP"
        const val ACTION_ENABLE_ACCESSIBILITY = "href.cleverty.remote.ENABLE_ACCESSIBILITY"
        const val ACTION_ENABLE_START_ON_BOOT = "href.cleverty.remote.ENABLE_START_ON_BOOT"
        const val ACTION_START_SERVICE = "href.cleverty.remote.START_SERVICE"
        const val ACTION_GET_RUSTDESK_ID = "href.cleverty.remote.GET_RUSTDESK_ID"
        const val ACTION_GRANT_PERMISSIONS = "href.cleverty.remote.GRANT_PERMISSIONS"
        const val ACTION_SET_PASSWORD = "href.cleverty.remote.SET_PASSWORD"
        const val ACTION_SET_DEVICE_NAME = "href.cleverty.remote.SET_DEVICE_NAME"

        const val EXTRA_RUSTDESK_ID = "rustdesk_id"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_DEVICE_NAME = "device_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_ENTERPRISE_SETUP -> {
                // Full setup - do everything
                // 1. Grant all permissions via root first
                grantAllPermissionsViaRoot(context)
                // 2. Enable start on boot
                enableStartOnBoot(context)
                // 3. Start the service first (app process must be running for accessibility to bind)
                startMainService(context)
                // 4. Wait for service to start
                Thread.sleep(2000)
                // 5. Enable accessibility service (system will bind since app is now running)
                enableAccessibilityService(context)
                // 6. Wait for accessibility to bind
                Thread.sleep(1000)
                val id = getRemoteId()
                Log.i(TAG, "Enterprise setup complete. Cleverty Remote ID: $id")
                setResultData(id)
            }
            ACTION_ENABLE_ACCESSIBILITY -> {
                enableAccessibilityService(context)
            }
            ACTION_ENABLE_START_ON_BOOT -> {
                enableStartOnBoot(context)
            }
            ACTION_START_SERVICE -> {
                startMainService(context)
            }
            ACTION_GET_RUSTDESK_ID -> {
                val id = getRemoteId()
                Log.i(TAG, "Cleverty Remote ID: $id")
                setResultData(id)
            }
            ACTION_GRANT_PERMISSIONS -> {
                grantAllPermissionsViaRoot(context)
            }
            ACTION_SET_PASSWORD -> {
                val password = intent.getStringExtra(EXTRA_PASSWORD)
                if (!password.isNullOrEmpty()) {
                    setPassword(password)
                } else {
                    Log.e(TAG, "SET_PASSWORD called without password extra")
                }
            }
            ACTION_SET_DEVICE_NAME -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                if (!deviceName.isNullOrEmpty()) {
                    setDeviceName(deviceName)
                } else {
                    Log.e(TAG, "SET_DEVICE_NAME called without device_name extra")
                }
            }
        }
    }

    /**
     * Set device name and restart rendezvous to apply
     * Uses setOption (not setLocalOption) because sync.rs reads from Config::get_option()
     */
    private fun setDeviceName(name: String) {
        try {
            ffi.FFI.setOption("preset-device-name", name)
            Log.i(TAG, "Device name set: $name")
            // Restart rendezvous to send new name to server
            ffi.FFI.restartRendezvous()
            Log.i(TAG, "Rendezvous restarted to apply new device name")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set device name: ${e.message}")
        }
    }

    /**
     * Set permanent password for auto-accept connections
     * Call via:
     *   am broadcast -a href.cleverty.remote.SET_PASSWORD --es password "your_password" -n com.carriez.flutter_hbb/.EnterpriseSetupReceiver
     */
    private fun setPassword(password: String) {
        EnterpriseConfig.setPassword(password)
        Log.i(TAG, "Permanent password set successfully")
    }

    /**
     * Enable accessibility service via Settings.Secure
     * Always clears and re-enables to ensure clean state
     * Requires: WRITE_SECURE_SETTINGS permission (granted via root/device owner)
     */
    private fun enableAccessibilityService(context: Context) {
        try {
            val componentName = ComponentName(context, InputService::class.java)
            val flattenedName = componentName.flattenToString()

            // First, clear all accessibility services
            Log.d(TAG, "Clearing accessibility services")
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ""
            )
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )

            // Small delay to ensure settings are cleared
            Thread.sleep(100)

            // Now enable only our service
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                flattenedName
            )

            // Enable accessibility master switch
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )

            Log.i(TAG, "Accessibility service enabled: $flattenedName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service: ${e.message}")
            Log.e(TAG, "Make sure WRITE_SECURE_SETTINGS permission is granted")
        }
    }

    /**
     * Enable start on boot via SharedPreferences
     * Uses device-protected storage to work before user unlock
     */
    private fun enableStartOnBoot(context: Context) {
        try {
            // Use device-protected storage context for direct boot support
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            val prefs = storageContext.getSharedPreferences(KEY_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_START_ON_BOOT_OPT, true).apply()
            Log.i(TAG, "Start on boot enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable start on boot: ${e.message}")
        }
    }

    /**
     * Start the main Cleverty Remote service
     * Grants PROJECT_MEDIA via root first, then starts service
     */
    private fun startMainService(context: Context) {
        try {
            // Grant MediaProjection permission via root
            val packageName = context.packageName
            executeRootCommand("appops set $packageName PROJECT_MEDIA allow")
            Log.i(TAG, "PROJECT_MEDIA granted via root")

            // Start the service
            val serviceIntent = Intent(context, MainService::class.java).apply {
                action = ACT_INIT_MEDIA_PROJECTION_AND_SERVICE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Main service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start main service: ${e.message}")
        }
    }

    /**
     * Get the Cleverty Remote ID from the Rust FFI
     */
    private fun getRemoteId(): String {
        return EnterpriseConfig.getRemoteId()
    }

    /**
     * Grant runtime permissions via shell commands
     * This requires root or device owner
     *
     * Call from your device owner app or via:
     *   adb shell pm grant com.carriez.flutter_hbb <permission>
     */
    private fun grantPermissionsViaShell(context: Context) {
        val packageName = context.packageName
        val permissions = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.RECORD_AUDIO",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WRITE_SECURE_SETTINGS"
        )

        Log.i(TAG, "Granting permissions for $packageName")
        Log.i(TAG, "Run these commands as root or device owner:")

        for (permission in permissions) {
            Log.i(TAG, "  pm grant $packageName $permission")
        }

        // Note: Actually executing these requires root shell access
        // Your device owner app should call these via Runtime.exec() with su
    }

    /**
     * Grant ALL permissions via root shell commands
     * This executes the commands directly using su
     */
    private fun grantAllPermissionsViaRoot(context: Context) {
        val packageName = context.packageName

        // All permissions to grant
        val permissions = listOf(
            // Storage
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            // Audio
            "android.permission.RECORD_AUDIO",
            // Overlay
            "android.permission.SYSTEM_ALERT_WINDOW",
            // Battery
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            // Notifications (Android 13+)
            "android.permission.POST_NOTIFICATIONS",
            // Secure settings (for accessibility)
            "android.permission.WRITE_SECURE_SETTINGS",
            // Screen capture without MediaProjection (system app)
            "android.permission.CAPTURE_VIDEO_OUTPUT",
            "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT",
            "android.permission.READ_FRAME_BUFFER"
        )

        Log.i(TAG, "Granting all permissions via root for $packageName")

        // Grant each permission
        for (permission in permissions) {
            executeRootCommand("pm grant $packageName $permission")
        }

        // Disable battery optimization
        executeRootCommand("dumpsys deviceidle whitelist +$packageName")

        // Grant overlay permission via appops
        executeRootCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")

        // MediaProjection - auto-approve via appops
        executeRootCommand("appops set $packageName PROJECT_MEDIA allow")

        Log.i(TAG, "All permissions granted")
    }

    /**
     * Execute a command with root (su)
     */
    private fun executeRootCommand(command: String): Boolean {
        return try {
            Log.d(TAG, "Executing: $command")
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            outputStream.write("$command\n".toByteArray())
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "Command succeeded: $command")
                true
            } else {
                Log.w(TAG, "Command failed with exit code $exitCode: $command")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $command - ${e.message}")
            false
        }
    }
}
