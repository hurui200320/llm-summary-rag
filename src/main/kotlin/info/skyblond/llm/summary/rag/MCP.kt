package info.skyblond.llm.summary.rag

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import info.skyblond.llm.summary.rag.db.*
import info.skyblond.llm.summary.rag.mcp.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import info.skyblond.llm.summary.rag.service.EmbeddingGenerator
import info.skyblond.llm.summary.rag.service.Lucene
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.inList
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.sortedBy
import org.ktorm.entity.take

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

    mcpServer.addTool(
        name = "get_doc_summary",
        description = "Get document summary by ids. The summary provides an overview for the document.",
        inputSchema = ToolSchema(
            properties = GetDocSummaryRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = GetDocSummaryRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = ListChunksRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = ListChunksRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = SearchDocsRagRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = SearchDocsRagRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = SearchChunksRagRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = SearchChunksRagRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = SearchKwAllRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = SearchKwAllRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = SearchKwAnyRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = SearchKwAnyRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
        inputSchema = ToolSchema(
            properties = ReadChunkRequest::class.jsonSchema["properties"]!!.jsonObject,
            required = ReadChunkRequest::class.jsonSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
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
