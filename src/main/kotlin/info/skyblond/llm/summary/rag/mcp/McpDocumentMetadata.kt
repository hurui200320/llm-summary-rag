package info.skyblond.llm.summary.rag.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpDocumentMetadata(
    val id: Int,
    val title: String,
    val author: String,
    val language: String
)
