package info.skyblond

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.model.output.structured.Description
import info.skyblond.db.ChunkSummaryVectors
import info.skyblond.db.Chunks
import info.skyblond.db.DocumentSummaryVectors
import info.skyblond.db.Documents
import info.skyblond.db.cosineDistance
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
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
    private val luceneAnalyzer = createAnalyzer()
    private val luceneParser = QueryParser("content", luceneAnalyzer)

    init {
        // require all match due to N gram tokenization
        luceneParser.defaultOperator = QueryParser.Operator.AND
    }

    @Description("Document")
    data class DocumentResult(
        @Description("Document id")
        val id: Int,
        @Description("Document title")
        val title: String,
        @Description("Document author")
        val author: String,
        @Description("Document language")
        val language: String,
        @Description("Document summary")
        val summary: String,
    )

    @Tool("List all known documents in the database")
    fun listDocuments(): List<DocumentResult> {
        logger.info("List all documents")
        return database.sequenceOf(Documents)
            .sortedBy { it.id }
            .map {
                DocumentResult(it.id, it.title, it.author, it.language, it.summary)
            }
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
    fun searchDocSummaryRAG(query: String): List<DocumentResult> {
        logger.info("Search document summary with query '$query'")
        val queryEmbedding = queryEmbedding(geminiClient, query)
        val result = docSummaryVectors
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(5)
        return result.map {
            DocumentResult(
                it.document.id,
                it.document.title,
                it.document.author,
                it.document.language,
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
        val queryEmbedding = queryEmbedding(geminiClient, query)
        val result = chunkSummaryVectors
            .filter { vec -> vec.chunkId.inList(chunkIds)}
            .sortedBy { it.summary.cosineDistance(queryEmbedding) }
            .take(5)
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
        val luceneQuery = luceneParser.parse(keywords)
        val hits = luceneSearcher.search(luceneQuery, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (all match): '$keywords'")
        return hits.scoreDocs.map {
            KeywordSearchResult.fromHit(it, luceneSearcher)
        }
    }

    @Tool("Do a lucene-backed keyword search on raw chunk content, allow any keywords match")
    fun searchKeywordAny(
        @P("Keywords, multiple keywords should be separated by space") keywords: String
    ): List<KeywordSearchResult> {
        val words = keywords.trim().split("\\s+".toRegex())
        val queryBuilder = BooleanQuery.Builder()

        words.forEach { word ->
            // due to N-gram, we need to match all sub pairs in a keyword
            // so the parser should still use AND operator
            // but to allow OR, we use sub query:
            // (AB and BC and CD) OR (EF and FG and GH)
            // so this will match either ABCD or EFGH
            queryBuilder.add(
                luceneParser.parse(word),
                BooleanClause.Occur.SHOULD
            )
        }
        val luceneQuery = queryBuilder.build()
        val hits = luceneSearcher.search(luceneQuery, 5)
        logger.info("Found ${hits.totalHits} hits for keyword (any match): '$keywords'")
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