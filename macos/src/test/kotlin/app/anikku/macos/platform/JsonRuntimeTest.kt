package app.anikku.macos.platform

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonRuntimeTest {
    @Test
    fun `org json parses and emits standards compliant JSON`() {
        val parsed = JSONObject("""{"name":"Anikku","items":[1,2]}""")
        assertEquals("Anikku", parsed.getString("name"))
        assertEquals(2, parsed.getJSONArray("items").length())

        val emitted = JSONObject()
            .put("action", "add")
            .put("files", JSONArray().put("episode.mkv"))
            .toString()
        assertTrue(emitted.startsWith("{"))
        assertEquals("add", JSONObject(emitted).getString("action"))
    }
}
