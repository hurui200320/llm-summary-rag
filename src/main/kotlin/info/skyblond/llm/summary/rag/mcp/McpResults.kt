package info.skyblond.llm.summary.rag.mcp

import kotlinx.serialization.Serializable
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import kotlin.text.toInt

@Serializable
data class McpDocumentMetadata(
    val id: Int,
    val title: String,
    val author: String,
    val language: String
)


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
) {
    companion object {

        fun TopDocs.toResultList(luceneSearcher: IndexSearcher): List<McpKeywordSearchResult> =
            this.scoreDocs.map { hit ->
                val doc = luceneSearcher.storedFields().document(hit.doc)
                McpKeywordSearchResult(
                    docId = doc.get("documentId")!!.toInt(),
                    chunkIndex = doc.get("chunkIndex")!!.toInt(),
                    score = hit.score,
                    content = doc.get("content")!!
                )
            }

    }
}
