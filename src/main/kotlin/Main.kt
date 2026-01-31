package info.skyblond

import com.google.genai.Client
import info.skyblond.db.ChunkSummaryVectors
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import info.skyblond.db.cosineDistance
import org.ktorm.dsl.eq
import org.ktorm.dsl.inList
import org.ktorm.entity.*

private val client by lazy { Client.builder().apiKey(geminiApiKey).build() }

fun main() {
    val documents = database.sequenceOf(Documents).find { it.id eq 1 }!!
    val chunks = database.sequenceOf(Chunks).filter { it.documentId eq 1 }
    val chunkVectors = database.sequenceOf(ChunkSummaryVectors)

    val query = "佐伯沙弥香家里允许她在中学时带手机上学吗？"
    println("Query: $query")
    val queryEmbedding = queryEmbedding(client, query)

    val result = chunkVectors
        .filter { vec -> vec.chunkId.inList(chunks.map { it.id })}
        .sortedBy { it.summary.cosineDistance(queryEmbedding) }
        .take(10)

    result.forEach {
        println("cos distance = ${it.embedding.cosineDistance(queryEmbedding)}")
        println(it.chunk.summary)
    }
}

