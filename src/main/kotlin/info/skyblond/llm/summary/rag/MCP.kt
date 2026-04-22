package info.skyblond.llm.summary.rag

import info.skyblond.llm.summary.rag.db.*
import info.skyblond.llm.summary.rag.mcp.*
import info.skyblond.llm.summary.rag.service.EmbeddingGenerator
import info.skyblond.llm.summary.rag.service.Lucene
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.inList
import org.ktorm.entity.*

fun main() {
    val luceneReader = DirectoryReader.open(luceneDir)
    val luceneSearcher = IndexSearcher(luceneReader)

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

    val defaultToolHint = ToolAnnotations(
        readOnlyHint = true,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = false
    )

    mcpServer.addTool(
        name = "list_documents",
        description = "List all known documents in the database, returns only short metadata (summary not included)",
        inputSchema = toolSchema { },
        toolAnnotations = defaultToolHint
    ) { _ ->
        val docs = database.sequenceOf(Documents)
            .sortedBy { it.id }
            .map { McpDocumentMetadata(it.id, it.title, it.author, it.language) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(docs))))
    }

    mcpServer.addTool(
        name = "get_doc_metadata",
        description = "Get document metadata by ids.",
        inputSchema = toolSchema {
            arrayIntParam("ids", "A list of document ids")
        },
        toolAnnotations = defaultToolHint
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

    mcpServer.addTool(
        name = "get_doc_summary",
        description = "Get document summary by ids. The summary provides an overview for the document.",
        inputSchema = toolSchema {
            arrayIntParam("ids", "A list of document ids")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val ids = request.arguments?.get("ids")?.let { element ->
            if (element is JsonArray) {
                element.map { it.jsonPrimitive.int }
            } else {
                emptyList()
            }
        } ?: emptyList()
        val docs = ids.mapNotNull { id -> database.sequenceOf(Documents).find { it.id eq id } }
            .map { McpDocumentSummary(it.id, it.summary) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(docs))))
    }

    mcpServer.addTool(
        name = "list_chunks",
        description = "List all chunks (index and summary) in a document",
        inputSchema = toolSchema {
            intParam("docId", "Document id")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val docId = request.arguments?.get("docId")?.jsonPrimitive?.int ?: -1
        val chunks = database.sequenceOf(Chunks)
            .filter { it.documentId eq docId }
            .sortedBy { it.indexOfDoc }
            .map { McpChunkResult(it.indexOfDoc, it.summary) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(chunks))))
    }

    mcpServer.addTool(
        name = "search_docs_rag",
        description = "Perform RAG (embedding) search on document summary",
        inputSchema = toolSchema {
            stringParam("query", "Search query")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
        val queryEmbedding = EmbeddingGenerator.queryEmbedding(geminiClient, query)
        val result = database.sequenceOf(DocumentSummaryVectors)
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(8)
        val summaries = result.map { McpDocumentSummary(it.document.id, it.document.summary) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(summaries))))
    }

    mcpServer.addTool(
        name = "search_chunks_rag",
        description = "Perform RAG (embedding) search on chunk summary",
        inputSchema = toolSchema {
            intParam("docId", "Document id")
            stringParam("query", "Search query")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val docId = request.arguments?.get("docId")?.jsonPrimitive?.int ?: -1
        val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
        val queryEmbedding = EmbeddingGenerator.queryEmbedding(geminiClient, query)
        val chunkIds = database.sequenceOf(Chunks).filter { it.documentId eq docId }.map { it.id }
        val result = database.sequenceOf(ChunkSummaryVectors)
            .filter { vec -> vec.chunkId.inList(chunkIds) }
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(8)
        val chunks = result.map { McpChunkResult(it.chunk.indexOfDoc, it.chunk.summary) }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(chunks))))
    }

    mcpServer.addTool(
        name = "search_kw_all",
        description = "Do a lucene-backed keyword search on raw chunk content, require all keywords match",
        inputSchema = toolSchema {
            stringParam("keywords", "Keywords, multiple keywords should be separated by space")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val keywords = request.arguments?.get("keywords")?.jsonPrimitive?.content ?: ""
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAllMatch(words, luceneSearcher, 5)
        val results = hits.scoreDocs.map { hit ->
            val doc = luceneSearcher.storedFields().document(hit.doc)
            McpKeywordSearchResult(
                docId = doc.get("documentId")!!.toInt(),
                chunkIndex = doc.get("chunkIndex")!!.toInt(),
                score = hit.score,
                content = doc.get("content")!!
            )
        }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(results))))
    }

    mcpServer.addTool(
        name = "search_kw_any",
        description = "Do a lucene-backed keyword search on raw chunk content, allow any keywords match",
        inputSchema = toolSchema {
            stringParam("keywords", "Keywords, multiple keywords should be separated by space")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val keywords = request.arguments?.get("keywords")?.jsonPrimitive?.content ?: ""
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAnyMatch(words, luceneSearcher, 5)
        val results = hits.scoreDocs.map { hit ->
            val doc = luceneSearcher.storedFields().document(hit.doc)
            McpKeywordSearchResult(
                docId = doc.get("documentId")!!.toInt(),
                chunkIndex = doc.get("chunkIndex")!!.toInt(),
                score = hit.score,
                content = doc.get("content")!!
            )
        }
        CallToolResult(content = listOf(TextContent(Json.encodeToString(results))))
    }

    mcpServer.addTool(
        name = "read_chunk",
        description = "Read a chunk, returning the content",
        inputSchema = toolSchema {
            intParam("docId", "Document id")
            intParam("chunkIndex", "Chunk index, start from 1")
        },
        toolAnnotations = defaultToolHint
    ) { request ->
        val docId = request.arguments?.get("docId")?.jsonPrimitive?.int ?: -1
        val chunkIdx = request.arguments?.get("chunkIndex")?.jsonPrimitive?.int ?: -1
        val result = database.sequenceOf(Chunks)
            .find { (it.documentId eq docId) and (it.indexOfDoc eq chunkIdx) }
        CallToolResult(content = listOf(TextContent(result?.content ?: "Chunk not found")))
    }

    println("Starting MCP server on port 3000...")
    embeddedServer(Netty, port = 3000) {
        mcpStreamableHttp {
            mcpServer
        }

        install(CORS) {
            anyHost()
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
