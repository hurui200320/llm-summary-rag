package info.skyblond.llm.summary.rag.mcp

import kotlinx.schema.Schema
import kotlinx.schema.Description

@Schema
data class GetDocSummaryRequest(
    @param:Description("A list of document ids")
    val ids: List<Int>
)

@Schema
data class ListChunksRequest(
    @param:Description("Document id")
    val docId: Int
)

@Schema
data class SearchDocsRagRequest(
    @param:Description("Search query")
    val query: String
)

@Schema
data class SearchChunksRagRequest(
    @param:Description("Document id")
    val docId: Int,
    @param:Description("Search query")
    val query: String
)

@Schema
data class SearchKwAllRequest(
    @param:Description("Keywords, multiple keywords should be separated by space")
    val keywords: String
)

@Schema
data class SearchKwAnyRequest(
    @param:Description("Keywords, multiple keywords should be separated by space")
    val keywords: String
)

@Schema
data class ReadChunkRequest(
    @param:Description("Document id")
    val docId: Int,
    @param:Description("Chunk index, start from 1")
    val chunkIndex: Int
)
