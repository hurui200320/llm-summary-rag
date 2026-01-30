package info.skyblond

import dev.langchain4j.model.googleai.BatchRequestResponse.*
import org.ktorm.dsl.eq
import org.ktorm.entity.*


private const val summaryLength = 200
private val batchBackend = Gemini3.flashBatch

/**
 * Create summary batch job using Langchain4j Gemini Batch API.
 * This run will gather chunks from database and create batch request.
 * */
fun main() {
    // 終將成為妳 關於佐伯沙彌香 1
    val document = database.sequenceOf(Documents).find { it.id eq 1 }!!

    val chatRequests = database.sequenceOf(Chunks)
        .filter { it.documentId eq document.id }
        .sortedBy { it.indexOfDoc }
        .map { generateChatRequest(it, summaryLength) }

    val batchName = when (val initialResponse = batchBackend.createBatchInline(
        "batch", 0, chatRequests
    )) {
        is BatchIncomplete -> initialResponse.batchName
        is BatchSuccess -> initialResponse.batchName
        is BatchError -> error("Batch creation failed: ${initialResponse.message}")
    }.value

    println("==================== BEGIN batch name ====================")
    println(batchName)
    println("==================== END batch name ====================")
}
