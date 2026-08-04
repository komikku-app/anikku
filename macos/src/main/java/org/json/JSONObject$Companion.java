package org.json;

/*
Public Domain.
*/

/**
 * Kotlin-companion shim (Anikku addition).
 *
 * Prebuilt anime extensions compiled against a Kotlin build of org.json
 * reference {@code JSONObject.NULL} as {@code JSONObject$Companion.getNULL()}
 * (see the {@code Companion} field on {@link JSONObject}). This class provides
 * that entry point; {@link #getNULL()} returns the real {@link JSONObject#NULL}
 * sentinel so behavior is identical to the plain static field.
 */
public final class JSONObject$Companion {

    // Package-private: instantiated only from the Companion field on JSONObject.
    JSONObject$Companion() {
    }

    /** Returns the {@link JSONObject#NULL} sentinel (same value as {@code JSONObject.NULL}). */
    public Object getNULL() {
        return JSONObject.NULL;
    }
}
