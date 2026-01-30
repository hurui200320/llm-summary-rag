package info.skyblond

import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.output.FinishReason
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import java.util.concurrent.CompletableFuture


private const val summaryLength = 200
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
    // 終將成為妳 關於佐伯沙彌香 1
    val document = database.sequenceOf(Documents).find { it.id eq 1 }!!

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
                println()
                chunk.summary = resp.aiMessage().text()
                chunk.flushChanges()
            }
        }
    }

    futures.forEach { it.get() }
}
