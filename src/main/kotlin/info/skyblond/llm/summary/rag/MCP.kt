package info.skyblond.llm.summary.rag

import info.skyblond.llm.summary.rag.db.Documents
import info.skyblond.llm.summary.rag.mcp.GetDocMetadataRequest
import info.skyblond.llm.summary.rag.mcp.McpDocumentMetadata
import info.skyblond.llm.summary.rag.mcp.jsonSchema
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.sortedBy


fun main() {
    val mcpServer = Server(
        serverInfo = Implementation(
            name = "llm-summary-rag",
            version = "0.0.1"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    mcpServer.addTool(
        name = "list_documents",
        description = "List all known documents in the database, returns only short metadata (summary not included)",
        inputSchema = ToolSchema(
            properties = buildJsonObject { }
        )
    ) { _ ->
        val docs = database.sequenceOf(Documents)
            .sortedBy { it.id }
            .map { McpDocumentMetadata(it.id, it.title, it.author, it.language) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(docs))))
    }

    mcpServer.addTool(
        name = "get_doc_metadata",
        description = "Get document metadata by ids.",
        inputSchema = ToolSchema(
            properties = GetDocMetadataRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = GetDocMetadataRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        ),
        toolAnnotations = ToolAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
            openWorldHint = false
        )
    ) { request ->
        val ids = request.arguments?.get("ids")?.let { element ->
            if (element is JsonArray) {
                element.map { it.jsonPrimitive.int }
            } else {
                emptyList()
            }
        } ?: emptyList()

        val docs = ids.mapNotNull { id ->
            database.sequenceOf(Documents).find { it.id eq id }
        }.map { McpDocumentMetadata(it.id, it.title, it.author, it.language) }

        CallToolResult(content = listOf(TextContent(Json.encodeToString(docs))))
    }


    println("Starting MCP server on port 3000...")

    embeddedServer(Netty, port = 3000) {
        // Support for Streamable HTTP Transport
        mcpStreamableHttp {
            mcpServer
        }

        install(CORS) {
            anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
            allowHeader(HttpHeaders.Authorization)
            allowNonSimpleContentTypes = true
            allowHeader("Mcp-Session-Id")
            allowHeader("Mcp-Protocol-Version")
            exposeHeader("Mcp-Session-Id")
            exposeHeader("Mcp-Protocol-Version")
        }
    }.start(wait = true)
}
