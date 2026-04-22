package info.skyblond.llm.summary.rag.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpDocumentSummary(
    val id: Int,
    val summary: String
)

@Serializable
data class McpChunkResult(
    val chunkIndex: Int,
    val summary: String
)

@Serializable
data class McpKeywordSearchResult(
    val docId: Int,
    val chunkIndex: Int,
    val score: Float,
    val content: String
)
