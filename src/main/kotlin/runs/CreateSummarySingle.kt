package info.skyblond.runs

import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.output.FinishReason
import info.skyblond.database
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import info.skyblond.generateChatRequest
import info.skyblond.openaiApiKey
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import java.util.concurrent.CompletableFuture


private const val summaryLength = 500
private val backend = OpenAiChatModel.builder()
    .apiKey(openaiApiKey)
    .baseUrl("https://openrouter.ai/api/v1")
    .modelName("openai/gpt-5.1")
    .reasoningEffort("high")
    .temperature(0.4)
    .topP(0.95)
    .build()

/**
 * Create summary batch job using Langchain4j normal API.
 * This run will gather chunks with empty summary and use regular API to process it.
 * */
fun main() {
    // TODO: Check id before running
    val document = database.sequenceOf(Documents).find { it.id eq 2 }!!

    val chunks = database.sequenceOf(Chunks)
        .filter { it.documentId eq document.id }
        .filter { it.summary eq "" }
        .sortedBy { it.indexOfDoc }

    val lock = Any()

    val futures = chunks.map { chunk ->
        CompletableFuture.runAsync {
            val chatReq = generateChatRequest(chunk, summaryLength)
            val resp = backend.chat(chatReq)
            synchronized(lock) {
                println(chunk.indexOfDoc)
                println(resp.finishReason())
                require(resp.finishReason() == FinishReason.STOP)
                println(resp.aiMessage().text())
                println(resp.aiMessage().text().length)
                println()
                chunk.summary = resp.aiMessage().text()
                chunk.flushChanges()
            }
        }
    }

    futures.forEach { it.get() }
}
