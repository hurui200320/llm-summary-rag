package info.skyblond.llm.summary.rag

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.Serializable
import info.skyblond.llm.summary.rag.db.Chunk
import info.skyblond.llm.summary.rag.db.ChunkSummaryVectors
import info.skyblond.llm.summary.rag.db.Chunks
import info.skyblond.llm.summary.rag.db.DocumentSummaryVectors
import info.skyblond.llm.summary.rag.db.Documents
import info.skyblond.llm.summary.rag.db.cosineDistance
import info.skyblond.llm.summary.rag.service.EmbeddingGenerator
import info.skyblond.llm.summary.rag.service.Lucene
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.inList
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.map
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.sortedBy
import org.ktorm.entity.take
import org.slf4j.LoggerFactory

/**
 * Tool for agent
 * */
class AgentTool : ToolSet {
    private val logger = LoggerFactory.getLogger(AgentTool::class.java)

    private val docSummaryVectors = database.sequenceOf(DocumentSummaryVectors)
    private val chunkSummaryVectors = database.sequenceOf(ChunkSummaryVectors)
    private val chunks = database.sequenceOf(Chunks)

    private val luceneReader = DirectoryReader.open(luceneDir)
    private val luceneSearcher = IndexSearcher(luceneReader)


    @Serializable
    data class DocumentResult(
        val id: Int,
        val title: String,
        val author: String,
        val language: String
    )

    @Tool
    @LLMDescription("List all known documents in the database, returns only short metadata (summary not included)")
    fun listDocumentMetadata(): List<DocumentResult> {
        logger.info("List all documents' metadata")
        return database.sequenceOf(Documents)
            .sortedBy { it.id }
            .map {
                DocumentResult(it.id, it.title, it.author, it.language)
            }
    }

    @Tool
    @LLMDescription("Get document metadata by ids.")
    fun getDocumentMetadata(
        @LLMDescription("A list of document ids") ids: List<Int>
    ): List<DocumentResult> {
        logger.info("Get document metadata for ids: $ids")
        return ids.mapNotNull { id ->
            database.sequenceOf(Documents).find { it.id eq id }
        }.map {
            DocumentResult(it.id, it.title, it.author, it.language)
        }
    }

    @Serializable
    data class DocumentSummary(
        val id: Int,
        val summary: String,
    )

    @Tool
    @LLMDescription("Get document summary by ids. The summary provides an overview for the document. If the id doesn't exist, it will be ignored")
    fun getDocumentSummary(
        @LLMDescription("A list of document ids") ids: List<Int>
    ): List<DocumentSummary> {
        logger.info("Get document summary for ids: $ids")
        return ids.mapNotNull { id -> database.sequenceOf(Documents).find { it.id eq id } }
            .map { DocumentSummary(it.id, it.summary) }
    }

    @Serializable
    data class ChunkResult(
        val chunkIndex: Int,
        val summary: String,
    )

    @Tool
    @LLMDescription("List all chunks (index and summary) in a document")
    fun listChunks(
        @LLMDescription("Document id") docId: Int
    ): List<ChunkResult> {
        logger.info("List all chunks in document #$docId")
        return database.sequenceOf(Chunks)
            .filter { it.documentId eq docId }
            .sortedBy { it.indexOfDoc }
            .map {
                ChunkResult(it.indexOfDoc, it.summary)
            }
    }

    @Tool
    @LLMDescription("Perform RAG (embedding) search on document summary")
    fun searchDocSummaryRAG(
        @LLMDescription("Search query") query: String
    ): List<DocumentSummary> {
        logger.info("Search document summary with query '$query'")
        val queryEmbedding = EmbeddingGenerator.queryEmbedding(geminiClient, query)
        val result = docSummaryVectors
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(8)
        return result.map {
            DocumentSummary(
                it.document.id,
                it.document.summary
            )
        }
    }

    @Tool
    @LLMDescription("Perform RAG (embedding) search on chunk summary")
    fun searchChunkSummaryRAG(
        @LLMDescription("Document id") docId: Int,
        @LLMDescription("Search query") query: String
    ): List<ChunkResult> {
        val document = database.sequenceOf(Documents).find { it.id eq 1 }
            ?: throw IllegalArgumentException("Invalid document id (not found)")
        val chunkIds = chunks.filter { it.documentId eq document.id }.map { it.id }
        logger.info("Search chunk summary with query '$query' in document #$docId")
        val queryEmbedding = EmbeddingGenerator.queryEmbedding(geminiClient, query)
        val result = chunkSummaryVectors
            .filter { vec -> vec.chunkId.inList(chunkIds) }
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(8)
        return result.map {
            ChunkResult(
                it.chunk.indexOfDoc,
                it.chunk.summary,
            )
        }
    }

    @Serializable
    data class KeywordSearchResult(
        val docId: Int,
        val chunkIndex: Int,
        val score: Float,
        val content: String,
    ) {
        companion object {
            fun fromHit(hit: ScoreDoc, luceneSearcher: IndexSearcher): KeywordSearchResult {
                val docId = hit.doc
                val doc = luceneSearcher.storedFields().document(docId)
                return KeywordSearchResult(
                    doc.get("documentId")!!.toInt(),
                    doc.get("chunkIndex")!!.toInt(),
                    hit.score,
                    doc.get("content")!!
                )
            }
        }
    }

    @Tool
    @LLMDescription("Do a lucene-backed keyword search on raw chunk content, require all keywords match")
    fun searchKeywordAll(
        @LLMDescription("Keywords, multiple keywords should be separated by space") keywords: String
    ): List<KeywordSearchResult> {
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAllMatch(words, luceneSearcher, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (all match): $words")
        return hits.scoreDocs.map {
            KeywordSearchResult.fromHit(it, luceneSearcher)
        }
    }

    @Tool
    @LLMDescription("Do a lucene-backed keyword search on raw chunk content, allow any keywords match")
    fun searchKeywordAny(
        @LLMDescription("Keywords, multiple keywords should be separated by space") keywords: String
    ): List<KeywordSearchResult> {
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAnyMatch(words, luceneSearcher, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (any match): $words")
        return hits.scoreDocs.map {
            KeywordSearchResult.fromHit(it, luceneSearcher)
        }
    }

    @Tool
    @LLMDescription("Read a chunk, returning the content")
    fun readChunk(
        @LLMDescription("Document id") documentId: Int,
        @LLMDescription("Chunk index, start from 1") chunkIndex: Int
    ): String {
        logger.info("Read chunk $documentId:$chunkIndex")
        if (database.sequenceOf(Documents).find { it.id eq documentId } == null) {
            throw IllegalArgumentException("Invalid document id")
        }
        val result = database.sequenceOf(Chunks)
            .find { it.documentId.eq(documentId) and it.indexOfDoc.eq(chunkIndex) }
        if (result == null) {
            throw IllegalArgumentException("Chunk not found")
        }
        return result.content
    }
}
