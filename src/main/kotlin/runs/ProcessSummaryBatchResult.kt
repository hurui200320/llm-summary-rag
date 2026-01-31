package info.skyblond.runs

import com.google.genai.Client
import com.google.genai.types.GetBatchJobConfig
import info.skyblond.database
import info.skyblond.db.Chunks
import info.skyblond.geminiApiKey
import org.ktorm.dsl.and
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf

private const val batchName = "batches/uprz927u8r5hh1qs9mlh0t7bbr4deif1ba94"

/**
 * Read batch response using gemini sdk, since Langchain4j has issue
 * on processing failed responses (like PROHIBITED_CONTENT)
 * */
fun main() {
    val client = Client.builder().apiKey(geminiApiKey).build()
    val batchJob = client.batches.get(batchName, GetBatchJobConfig.builder().build())

    val chunks = database.sequenceOf(Chunks)

    var failedCount = 0

    batchJob.dest().get().inlinedResponses().get().forEachIndexed { index, response ->
        val indexOfDoc = index + 1
        val resp = response.response().get()
        if (resp.finishReason().knownEnum() != com.google.genai.types.FinishReason.Known.STOP) {
            failedCount++
            return@forEachIndexed
        }
        println(indexOfDoc)
        println(resp.text())

        chunks.find { it.documentId eq 1 and (it.indexOfDoc eq indexOfDoc) }?.apply {
            summary = resp.text() ?: ""
            flushChanges()
        }
    }

    println("Failed count: $failedCount")
}