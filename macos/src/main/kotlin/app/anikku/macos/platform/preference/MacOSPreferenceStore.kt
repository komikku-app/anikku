package app.anikku.macos.platform.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import app.anikku.macos.platform.storage.MacOSAtomicFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import io.github.oshai.kotlinlogging.KotlinLogging

private val preferenceLogger = KotlinLogging.logger {}

/**
 * macOS file-backed PreferenceStore implementation.
 * Stores all preferences as a JSON file at the configured path.
 * Uses kotlinx.serialization for reading/writing JSON.
 *
 * Data file: ~/Library/Application Support/Anikku/data/preferences.json
 */
class MacOSPreferenceStore(
    private val prefsFile: File,
    private val json: Json = Json { prettyPrint = true },
    private val writeText: (File, String) -> Unit = MacOSAtomicFile::writeText,
) : PreferenceStore {

    private val store = ConcurrentHashMap<String, JsonElement>()
    private val mutationLock = Any()
    private val persistenceError = AtomicReference<Throwable?>(null)
    private val keyFlow = MutableSharedFlow<String?>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        loadFromFile()
    }

    override fun getString(key: String, defaultValue: String): Preference<String> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(it) },
            deserialize = { it.jsonPrimitive.content },
            onChanged = { saveToFile() },
        )
    }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(it) },
            deserialize = { it.jsonPrimitive.long },
            onChanged = { saveToFile() },
        )
    }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(it) },
            deserialize = { it.jsonPrimitive.content.toInt() },
            onChanged = { saveToFile() },
        )
    }

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(it) },
            deserialize = { it.jsonPrimitive.double.toFloat() },
            onChanged = { saveToFile() },
        )
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(it) },
            deserialize = { it.jsonPrimitive.boolean },
            onChanged = { saveToFile() },
        )
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { value ->
                JsonArray(value.map { JsonPrimitive(it) })
            },
            deserialize = { element ->
                element.jsonArray.map { it.jsonPrimitive.content }.toSet()
            },
            onChanged = { saveToFile() },
        )
    }

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return JsonFilePreference(
            store = store,
            mutationLock = mutationLock,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serialize = { JsonPrimitive(serializer(it)) },
            deserialize = { deserializer(it.jsonPrimitive.content) },
            onChanged = { saveToFile() },
        )
    }

    /** Last persistence failure, if a write was rejected by the filesystem. */
    fun lastPersistenceError(): Throwable? = persistenceError.get()

    override fun getAll(): Map<String, *> {
        return synchronized(mutationLock) {
            store.toMap().mapValues { (_, element) ->
            try {
                element.jsonPrimitive.content
            } catch (_: Exception) {
                element.toString()
            }
            }
        }
    }

    /** Lossless JSON snapshot used by the portable backup format. */
    fun snapshotJson(): Map<String, JsonElement> = synchronized(mutationLock) {
        store.toMap()
    }

    /**
     * Atomically persists a typed preference snapshot.
     *
     * When [replace] is false, restored values override matching keys while
     * preferences introduced after the backup was made remain intact.
     */
    fun restoreJson(values: Map<String, JsonElement>, replace: Boolean = false) {
        synchronized(mutationLock) {
            val previous = store.toMap()
            if (replace) store.clear()
            store.putAll(values)
            try {
                saveToFile()
            } catch (error: Exception) {
                store.clear()
                store.putAll(previous)
                throw error
            }
            keyFlow.tryEmit(null)
        }
    }

    private fun loadFromFile() {
        if (!prefsFile.exists()) return
        try {
            val jsonObject = json.parseToJsonElement(prefsFile.readText()).jsonObject
            synchronized(mutationLock) { store.putAll(jsonObject) }
        } catch (error: Exception) {
            val backup = MacOSAtomicFile.preserveMalformed(prefsFile)
            preferenceLogger.warn(error) {
                "Preferences JSON is malformed; starting with defaults" +
                    (backup?.let { ", preserved at ${it.name}" } ?: "")
            }
        }
    }

    private fun saveToFile() {
        try {
            synchronized(mutationLock) {
                val content = json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.JsonObject(store),
                )
                writeText(prefsFile, content)
            }
            persistenceError.set(null)
        } catch (error: Exception) {
            persistenceError.set(error)
            preferenceLogger.error(error) { "Failed to persist preferences to ${prefsFile.path}" }
            throw error
        }
    }

    /**
     * Generic JSON file-backed preference implementation.
     */
    private class JsonFilePreference<T>(
        private val store: ConcurrentHashMap<String, JsonElement>,
        private val mutationLock: Any,
        private val keyFlow: MutableSharedFlow<String?>,
        private val key: String,
        private val defaultValue: T,
        private val serialize: (T) -> JsonElement,
        private val deserialize: (JsonElement) -> T,
        private val onChanged: () -> Unit,
    ) : Preference<T> {

        override fun key(): String = key

        override fun get(): T {
            val stored = store[key] ?: return defaultValue
            return try {
                deserialize(stored)
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun set(value: T) {
            synchronized(mutationLock) {
                val hadPrevious = store.containsKey(key)
                val previous = store[key]
                store[key] = serialize(value)
                try {
                    onChanged()
                } catch (error: Exception) {
                    if (hadPrevious) store[key] = previous!! else store.remove(key)
                    throw error
                }
                keyFlow.tryEmit(key)
            }
        }

        override fun isSet(): Boolean = synchronized(mutationLock) { store.containsKey(key) }

        override fun delete() {
            synchronized(mutationLock) {
                val hadPrevious = store.containsKey(key)
                val previous = store[key]
                store.remove(key)
                try {
                    onChanged()
                } catch (error: Exception) {
                    if (hadPrevious) store[key] = previous!!
                    throw error
                }
                keyFlow.tryEmit(key)
            }
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> {
            return keyFlow
                .filter { it == key || it == null }
                .onStart { emit(key) }
                .map { get() }
        }

        override fun stateIn(scope: CoroutineScope): StateFlow<T> {
            return changes().stateIn(scope, SharingStarted.Eagerly, get())
        }
    }
}
