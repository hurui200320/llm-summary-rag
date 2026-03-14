package info.skyblond

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.model.output.structured.Description
import info.skyblond.llm.summary.rag.database
import info.skyblond.llm.summary.rag.db.*
import info.skyblond.llm.summary.rag.geminiClient
import info.skyblond.llm.summary.rag.luceneDir
import info.skyblond.service.EmbeddingGenerator
import info.skyblond.service.Lucene
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.dsl.inList
import org.ktorm.entity.*
import org.slf4j.LoggerFactory

/**
 * Tool for agent
 * */
object AgentTool {
    private val logger = LoggerFactory.getLogger(AgentTool::class.java)

    private val docSummaryVectors = database.sequenceOf(DocumentSummaryVectors)
    private val chunkSummaryVectors = database.sequenceOf(ChunkSummaryVectors)
    private val chunks = database.sequenceOf(Chunks)

    private val luceneReader = DirectoryReader.open(luceneDir)
    private val luceneSearcher = IndexSearcher(luceneReader)


    @Description("Document")
    data class DocumentResult(
        @Description("Document id")
        val id: Int,
        @Description("Document title")
        val title: String,
        @Description("Document author")
        val author: String,
        @Description("Document language")
        val language: String
    )

    @Tool("List all known documents in the database, returns only short metadata (summary not included)")
    fun listDocumentMetadata(): List<DocumentResult> {
        logger.info("List all documents' metadata")
        return database.sequenceOf(Documents)
            .sortedBy { it.id }
            .map {
                DocumentResult(it.id, it.title, it.author, it.language)
            }
    }

    @Tool("Get document metadata by ids.")
    fun getDocumentMetadata(
        @P("A list of document ids") ids: List<Int>
    ): List<DocumentResult> {
        logger.info("Get document metadata for ids: $ids")
        return ids.mapNotNull { id ->
            database.sequenceOf(Documents).find { it.id eq id }
        }.map {
            DocumentResult(it.id, it.title, it.author, it.language)
        }
    }

    @Description("Document summary")
    data class DocumentSummary(
        @Description("Document id")
        val id: Int,
        @Description("Document summary")
        val summary: String,
    )

    @Tool("Get document summary by ids. The summary provides an overview for the document. If the id doesn't exist, it will be ignored")
    fun getDocumentSummary(
        @P("A list of document ids") ids: List<Int>
    ): List<DocumentSummary> {
        logger.info("Get document summary for ids: $ids")
        return ids.mapNotNull { id -> database.sequenceOf(Documents).find { it.id eq id } }
            .map { DocumentSummary(it.id, it.summary) }
    }

    @Description("Chunk")
    data class ChunkResult(
        @Description("Chunk index in the document, start from 1")
        val chunkIndex: Int,
        @Description("Chunk summary")
        val summary: String,
    )

    @Tool("List all chunks (index and summary) in a document")
    fun listChunks(docId: Int): List<ChunkResult> {
        logger.info("List all chunks in document #$docId")
        return database.sequenceOf(Chunks)
            .filter { it.documentId eq docId }
            .sortedBy { it.indexOfDoc }
            .map {
                ChunkResult(it.indexOfDoc, it.summary)
            }
    }

    @Tool("Perform RAG (embedding) search on document summary")
    fun searchDocSummaryRAG(query: String): List<DocumentSummary> {
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

    @Tool("Perform RAG (embedding) search on chunk summary")
    fun searchChunkSummaryRAG(docId: Int, query: String): List<ChunkResult> {
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

    @Description("Keyword search result")
    data class KeywordSearchResult(
        @Description("Document id")
        val docId: Int,
        @Description("Index of this chunk in the document, start from 1")
        val chunkIndex: Int,
        @Description("Score of this hit")
        val score: Float,
        @Description("Chunk content")
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

    @Tool("Do a lucene-backed keyword search on raw chunk content, require all keywords match")
    fun searchKeywordAll(
        @P("Keywords, multiple keywords should be separated by space") keywords: String
    ): List<KeywordSearchResult> {
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAllMatch(words, luceneSearcher, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (all match): $words")
        return hits.scoreDocs.map {
            KeywordSearchResult.fromHit(it, luceneSearcher)
        }
    }

    @Tool("Do a lucene-backed keyword search on raw chunk content, allow any keywords match")
    fun searchKeywordAny(
        @P("Keywords, multiple keywords should be separated by space") keywords: String
    ): List<KeywordSearchResult> {
        val words = keywords.trim().split("\\s+".toRegex())
        val hits = Lucene.searchIndexAnyMatch(words, luceneSearcher, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (any match): $words")
        return hits.scoreDocs.map {
            KeywordSearchResult.fromHit(it, luceneSearcher)
        }
    }

    @Tool("Read a chunk, returning the content")
    fun readChunk(
        @P("Document id") documentId: Int,
        @P("Chunk index, start from 1") chunkIndex: Int
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