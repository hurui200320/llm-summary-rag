package info.skyblond

import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.googleai.*
import dev.langchain4j.model.googleai.BatchRequestResponse.*
import dev.langchain4j.model.openai.OpenAiChatModel
import java.io.File


fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY") ?: error("GEMINI_API_KEY is not set")


    val gemini = GoogleAiGeminiBatchChatModel.builder()
        .apiKey(apiKey)
        .modelName("gemini-3-flash-preview")
        .safetySettings(
            GeminiHarmCategory.entries.map {
                GeminiSafetySetting(it, GeminiHarmBlockThreshold.BLOCK_NONE)
            }
        )
        .thinkingConfig(
            GeminiThinkingConfig.builder()
                // Gemini 3 only
                .thinkingLevel("high")
                .build()
        )
        .temperature(1.0)
        .topP(0.95)
        .topK(450)
        .build()


    val source = File(
        "/run/user/1000/gvfs/smb-share:server=100.99.241.120,share=data/txt/" +
                "終將成為妳 關於佐伯沙彌香 1 - 入間 人間.txt"
    ).readText()

    var buffer = source
    val chunks = mutableListOf<String>()

    println("==================== BEGIN prompt ====================")
    println(ChunkSlicer.systemMessage(null))
    println("==================== END prompt ====================")

    while (buffer.isNotBlank()) {
        val input = buffer.take(1000)
        println("==================== BEGIN input ====================")
        println(input)
        println("==================== END input ====================")
        val chatRequest = ChatRequest.builder()
            .messages(
                SystemMessage(ChunkSlicer.systemMessage(null)),
                UserMessage(input)
            )
            .toolSpecifications(
                ToolSpecifications.toolSpecificationsFrom(
                    EmbeddingTokenCountEstimator(geminiEmbedding001TokenEstimator)
                )
            )
            .build()
/*
        // create batch
        val batchName = when (val initialResponse = gemini.createBatchInline(
            "batch", 0, listOf(chatRequest)
        )) {
            is BatchIncomplete -> initialResponse.batchName
            is BatchSuccess -> initialResponse.batchName
            is BatchError -> error("Batch creation failed: ${initialResponse.message}")
        }
        println("==================== BEGIN batch name ====================")
        println(batchName.value)
        println("==================== END batch name ====================")
        // polling result
        var result: BatchResponse<ChatResponse>
        do {
            Thread.sleep(5_000)
            result = gemini.retrieveBatchResults(batchName)
        } while (result is BatchIncomplete)
        // parse result
        val response = when (result) {
            is BatchSuccess -> result.responses().first()
            is BatchError -> error("Batch failed: ${result.message()}")
        }
*/

        val response = gemini3flash.chat(chatRequest)
//        val response = openRouter.chat(chatRequest)

        val tail = response.aiMessage().text()
        val index = input.indexOf(tail)
        if (index == -1) continue // invalid tail
        val chunk = input.substring(0, index + tail.length)
        if (chunk.length == input.length) continue // selected the full input
        chunks.add(chunk)
        println("==================== BEGIN chunk ====================")
        println(chunk)
        println("==================== END chunk ====================")
        buffer = buffer.drop(chunk.length)
    }
}