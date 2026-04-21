package info.skyblond.llm.summary.rag

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.buildJsonObject

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

    // Basic "hello" tool for verification
    mcpServer.addTool(
        name = "hello",
        description = "A simple greeting tool",
        inputSchema = ToolSchema(
            properties = buildJsonObject { }
        )
    ) { _ ->
        CallToolResult(content = listOf(TextContent("world")))
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
