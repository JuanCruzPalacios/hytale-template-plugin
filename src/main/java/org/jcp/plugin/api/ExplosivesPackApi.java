package org.jcp.plugin.api;

/**
 * Public, stable integration API for ExplosivesPack.
 *
 * This is intentionally tiny and reflection-friendly:
 * other mods can Class.forName(...) this class and call static methods.
 */
public final class ExplosivesPackApi {

    /** Increment only when you make breaking changes to this API surface. */
    public static final int API_VERSION = 1;

    /** A stable identifier other mods can log/use. */
    public static final String PLUGIN_ID = "ExplosivesPack";

    // "Installed" is implied if this class can be loaded.
    private static volatile boolean active = false;

    // Indicates the plugin finished BootEvent pipeline (assets/world ready).
    private static volatile boolean booted = false;

    private ExplosivesPackApi() {}

    public static int getApiVersion() {
        return API_VERSION;
    }

    public static String getPluginId() {
        return PLUGIN_ID;
    }

    /**
     * True when the plugin loaded and setup() completed without fatal disable.
     * Note: For "fully ready", use isBooted().
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * True when BootEvent has fired and the plugin finished boot-time registrations.
     */
    public static boolean isBooted() {
        return booted;
    }

    // --- internal setters (called by the plugin) ---

    public static void _setActive(boolean value) {
        active = value;
    }

    public static void _setBooted(boolean value) {
        booted = value;
    }
}