package android.keyboard.engine

import kotlinx.serialization.json.Json

object LayoutJson {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(layout: KeyboardLayout): String = json.encodeToString(layout)

    fun decode(text: String): KeyboardLayout = json.decodeFromString(text)
}
