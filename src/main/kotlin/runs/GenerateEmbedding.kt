package info.skyblond.runs

import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import info.skyblond.database
import info.skyblond.db.ChunkSummaryVector
import info.skyblond.db.ChunkSummaryVectors
import info.skyblond.db.Chunks
import info.skyblond.db.DocumentSummaryVector
import info.skyblond.db.DocumentSummaryVectors
import info.skyblond.db.Documents
import info.skyblond.documentEmbedding
import info.skyblond.geminiApiKey
import org.ktorm.dsl.eq
import org.ktorm.dsl.neq
import org.ktorm.entity.*
import kotlin.collections.first
import kotlin.math.sqrt

private val client by lazy { Client.builder().apiKey(geminiApiKey).build() }

/**
 * Use gemini embedding 001 to generate embeddings and save to db.
 * */
fun main() {
    val document = database.sequenceOf(Documents).find { it.id eq 1 }!!
    val chunkVectors = database.sequenceOf(ChunkSummaryVectors)
    val documentVectors = database.sequenceOf(DocumentSummaryVectors)

    val chunks = database.sequenceOf(Chunks)
        .filter { it.documentId eq 1 }
        .filter { it.summary neq "" }
        .sortedBy { it.indexOfDoc }
        .toList()

    chunks.forEach { chunk ->
        println(chunk.indexOfDoc)
        val chunkEmbedding = documentEmbedding(client, chunk.summary, document.title)
        val vector = ChunkSummaryVector {
            this.chunk = chunk
            this.embedding = chunkEmbedding
        }
        chunkVectors.add(vector)
    }

    val docEmbedding = documentEmbedding(client, document.summary, document.title)
    documentVectors.add(DocumentSummaryVector {
        this.document = document
        this.embedding = docEmbedding
    })
}

