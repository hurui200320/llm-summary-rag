package info.skyblond.llm.summary.rag.mcp

import kotlinx.schema.Schema
import kotlinx.schema.Description

@Schema
data class GetDocMetadataRequest(
    @param:Description("A list of document ids")
    val ids: List<Int>
)

