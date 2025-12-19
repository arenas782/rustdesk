package com.carriez.flutter_hbb

import android.util.Log
import ffi.FFI

/**
 * Enterprise Configuration - Hardcoded server settings
 * All configuration is done via FFI calls
 */
object EnterpriseConfig {
    private const val TAG = "EnterpriseConfig"

    // Server configuration - MODIFY THESE VALUES FOR YOUR DEPLOYMENT
    const val RENDEZVOUS_SERVER = "rustdesk.cleverty.app"
    const val RELAY_SERVER = "rustdesk.cleverty.app"
    const val API_SERVER = "https://rustdesk.cleverty.app"
    const val PUBLIC_KEY = "R8zlNg4TEv9rbPYND8+odNkqcdVtXhE3mpvTg+DVm5I="

    // Enterprise settings
    const val APPROVE_MODE = "password"  // "password" = auto-accept with password, "click" = manual, "" = both
    const val DEVICE_NAME = ""  // Leave empty to use system hostname, or set custom name
    const val DEFAULT_PASSWORD = "6789123450"  // Default permanent password

    private var initialized = false

    /**
     * Initialize enterprise settings via FFI
     * Call this early in app startup (e.g., MainService.onCreate)
     */
    @Synchronized
    fun initialize() {
        if (initialized) {
            Log.d(TAG, "Enterprise config already initialized")
            return
        }

        try {
            Log.i(TAG, "Initializing enterprise configuration...")

            // Server configuration
            FFI.setLocalOption("custom-rendezvous-server", RENDEZVOUS_SERVER)
            FFI.setLocalOption("relay-server", RELAY_SERVER)
            FFI.setLocalOption("api-server", API_SERVER)
            FFI.setLocalOption("key", PUBLIC_KEY)

            // Auto-accept mode (no user interaction needed with correct password)
            FFI.setOption("approve-mode", APPROVE_MODE)

            // Device name (if set)
            if (DEVICE_NAME.isNotEmpty()) {
                FFI.setLocalOption("preset-device-name", DEVICE_NAME)
                Log.i(TAG, "Device name set: $DEVICE_NAME")
            }

            // Default password (if set)
            if (DEFAULT_PASSWORD.isNotEmpty()) {
                setPassword(DEFAULT_PASSWORD)
                Log.i(TAG, "Default password set")
            }

            // Enable all permissions for incoming connections
            FFI.setOption("enable-keyboard", "Y")
            FFI.setOption("enable-clipboard", "Y")
            FFI.setOption("enable-file-transfer", "Y")
            FFI.setOption("enable-audio", "Y")
            FFI.setOption("enable-tunnel", "Y")
            FFI.setOption("enable-remote-restart", "Y")

            // Direct IP access
            FFI.setOption("direct-server", "Y")
            FFI.setOption("direct-access-port", "21118")

            initialized = true
            Log.i(TAG, "Enterprise config initialized: server=$RENDEZVOUS_SERVER, approve-mode=$APPROVE_MODE")

            // Start the network service to connect to rendezvous server
            FFI.startService()
            Log.i(TAG, "Network service started")

            // Restart rendezvous connection to apply new server config
            FFI.restartRendezvous()
            Log.i(TAG, "Rendezvous connection restarted with new config")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize enterprise config: ${e.message}")
        }
    }

    /**
     * Set permanent password for auto-accept connections
     */
    fun setPassword(password: String) {
        try {
            FFI.setLocalOption("permanent-password", password)
            Log.i(TAG, "Permanent password set")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set password: ${e.message}")
        }
    }

    /**
     * Get current RustDesk ID
     */
    fun getRustDeskId(): String {
        return try {
            val id = FFI.getMyId()
            if (id.isNotEmpty()) id else "pending"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get RustDesk ID: ${e.message}")
            "error"
        }
    }

    /**
     * Check if enterprise config is initialized
     */
    fun isInitialized(): Boolean = initialized
}
