package info.skyblond.llm.summary.rag.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ToolSchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val requiredFields = mutableListOf<String>()

    fun stringParam(name: String, description: String, required: Boolean = true) {
        properties[name] = buildJsonObject {
            put("type", "string")
            put("description", description)
        }
        if (required) requiredFields.add(name)
    }

    fun intParam(name: String, description: String, required: Boolean = true) {
        properties[name] = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
        if (required) requiredFields.add(name)
    }

    fun arrayIntParam(name: String, description: String, required: Boolean = true) {
        properties[name] = buildJsonObject {
            put("type", "array")
            putJsonObject("items") {
                put("type", "integer")
            }
            put("description", description)
        }
        if (required) requiredFields.add(name)
    }

    fun build(): ToolSchema {
        return ToolSchema(
            properties = buildJsonObject {
                properties.forEach { (name, schema) ->
                    put(name, schema)
                }
            },
            required = requiredFields
        )
    }
}

fun toolSchema(block: ToolSchemaBuilder.() -> Unit): ToolSchema {
    return ToolSchemaBuilder().apply(block).build()
}
