package info.skyblond.llm.summary.rag.service

import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import info.skyblond.llm.summary.rag.database
import info.skyblond.llm.summary.rag.db.Chunk
import info.skyblond.llm.summary.rag.db.ChunkSummaryVector
import info.skyblond.llm.summary.rag.db.ChunkSummaryVectors
import info.skyblond.llm.summary.rag.db.Document
import info.skyblond.llm.summary.rag.db.DocumentSummaryVector
import info.skyblond.llm.summary.rag.db.DocumentSummaryVectors
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import kotlin.math.sqrt

object EmbeddingGenerator {
    private val chunkVectors = database.sequenceOf(ChunkSummaryVectors)
    private val documentVectors = database.sequenceOf(DocumentSummaryVectors)

    /**
     * Generating and save [document] summary embedding to the database.
     * */
    fun summaryEmbedding(client: Client, document: Document) {
        val docEmbedding = contentEmbedding(client, document.summary, document.title)
        val vector = DocumentSummaryVector {
            this.document = document
            this.embedding = docEmbedding
        }
        documentVectors.find { it.documentId eq document.id }?.delete()
        documentVectors.add(vector)
    }

    /**
     * Generating and save [chunk] summary embedding to the database.
     * */
    fun summaryEmbedding(client: Client, chunk: Chunk) {
        val chunkEmbedding = contentEmbedding(
            client, chunk.summary, "${chunk.document.title} (Chunk ${chunk.indexOfDoc})"
        )
        val vector = ChunkSummaryVector {
            this.chunk = chunk
            this.embedding = chunkEmbedding
        }
        chunkVectors.find { it.chunkId eq chunk.id }?.delete()
        chunkVectors.add(vector)
    }

    /**
     * Generating a [query] embedding.
     * */
    fun queryEmbedding(client: Client, query: String): List<Float> {
        val resp = client.models.embedContent(
            "gemini-embedding-001",
            query,
            EmbedContentConfig.builder()
                .taskType("RETRIEVAL_QUERY")
                .outputDimensionality(1536)
                .build()
        )
        val embedding = resp.embeddings().get().first()
        val value = embedding.values().get()
        return value.normalize()
    }

    private fun contentEmbedding(client: Client, content: String, title: String): List<Float> {
        val resp = client.models.embedContent(
            "gemini-embedding-001",
            content,
            EmbedContentConfig.builder()
                .taskType("RETRIEVAL_DOCUMENT")
                .outputDimensionality(1536)
                .title(title)
                .build()
        )
        val embedding = resp.embeddings().get().first()
        val value = embedding.values().get()
        return value.normalize()
    }

    private fun List<Float>.l2norm(): Float = sqrt(this.map { it * it }.sum())

    private fun List<Float>.normalize(): List<Float> {
        val norm = this.l2norm()
        if (norm == 0.0f) return this
        return this.map { it / norm }
    }
}