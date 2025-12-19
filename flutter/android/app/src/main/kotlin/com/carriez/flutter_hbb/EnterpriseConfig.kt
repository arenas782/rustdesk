package com.carriez.flutter_hbb

import android.util.Log

/**
 * Enterprise Configuration - Hardcoded server settings
 * All configuration is done via FFI calls, no Rust modifications needed
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
            ffi.FFI.setLocalOption("custom-rendezvous-server", RENDEZVOUS_SERVER)
            ffi.FFI.setLocalOption("relay-server", RELAY_SERVER)
            ffi.FFI.setLocalOption("api-server", API_SERVER)
            ffi.FFI.setLocalOption("key", PUBLIC_KEY)

            // Auto-accept mode (no user interaction needed with correct password)
            ffi.FFI.setLocalOption("approve-mode", APPROVE_MODE)

            // Enable all permissions for incoming connections
            ffi.FFI.setLocalOption("enable-keyboard", "Y")
            ffi.FFI.setLocalOption("enable-clipboard", "Y")
            ffi.FFI.setLocalOption("enable-file-transfer", "Y")
            ffi.FFI.setLocalOption("enable-audio", "Y")
            ffi.FFI.setLocalOption("enable-tunnel", "Y")
            ffi.FFI.setLocalOption("enable-remote-restart", "Y")

            // Direct IP access
            ffi.FFI.setLocalOption("direct-server", "Y")
            ffi.FFI.setLocalOption("direct-access-port", "21118")

            initialized = true
            Log.i(TAG, "Enterprise config initialized: server=$RENDEZVOUS_SERVER, approve-mode=$APPROVE_MODE")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize enterprise config: ${e.message}")
        }
    }

    /**
     * Set permanent password for auto-accept connections
     */
    fun setPassword(password: String) {
        try {
            ffi.FFI.setLocalOption("permanent-password", password)
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
            val id = ffi.FFI.getLocalOption("id")
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
