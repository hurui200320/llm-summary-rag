package info.skyblond.runs

import com.google.genai.Client
import info.skyblond.database
import info.skyblond.db.*
import info.skyblond.documentEmbedding
import info.skyblond.geminiApiKey
import org.ktorm.dsl.eq
import org.ktorm.dsl.neq
import org.ktorm.entity.*

private val client by lazy { Client.builder().apiKey(geminiApiKey).build() }

/**
 * Use gemini embedding 001 to generate embeddings and save to db.
 * */
fun main() {
    // TODO: Check id before running
    val document = database.sequenceOf(Documents).find { it.id eq 2 }!!
    val chunkVectors = database.sequenceOf(ChunkSummaryVectors)
    val documentVectors = database.sequenceOf(DocumentSummaryVectors)

    val chunks = database.sequenceOf(Chunks)
        .filter { it.documentId eq document.id }
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

