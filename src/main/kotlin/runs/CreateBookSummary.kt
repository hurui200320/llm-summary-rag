package info.skyblond.runs

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.output.FinishReason
import info.skyblond.createBookSummarySystemPrompt
import info.skyblond.database
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import info.skyblond.openaiApiKey
import org.ktorm.dsl.eq
import org.ktorm.entity.*
import org.ktorm.entity.sequenceOf

private const val summaryLength = 1000
private val backend by lazy {
    OpenAiChatModel.builder()
        .apiKey(openaiApiKey)
        .baseUrl("https://openrouter.ai/api/v1")
        .modelName("openai/gpt-5.1")
        .reasoningEffort("high")
        .temperature(0.4)
        .topP(0.95)
        .build()
}

/**
 * Create book summary.
 * */
fun main() {
    // 終將成為妳 關於佐伯沙彌香 1
    val document = database.sequenceOf(Documents).find { it.id eq 1 }!!
    val chunks = database.sequenceOf(Chunks)
        .filter { it.documentId eq document.id }
        .sortedBy { it.indexOfDoc }

    val systemPrompt = createBookSummarySystemPrompt(summaryLength)

    val input = chunks.joinToString("\n") {
        """
            |[Chunk ${it.indexOfDoc}]
            |${it.summary}
            |
        """.trimMargin()
    }.trim()

    val chatRequest = ChatRequest.builder()
        .messages(
            SystemMessage(systemPrompt),
            UserMessage(input)
        )
        .build()

    val resp = backend.chat(chatRequest)
    require(resp.finishReason() == FinishReason.STOP)
    println(resp.aiMessage().text())

    document.summary = resp.aiMessage().text()
    document.flushChanges()
}
