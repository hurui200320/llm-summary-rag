package info.skyblond

import dev.langchain4j.model.googleai.*
import dev.langchain4j.model.openai.OpenAiChatModel
import info.skyblond.db.PgVectorSqlDialect
import org.ktorm.database.Database

val geminiApiKey by lazy {
    System.getenv("GEMINI_API_KEY") ?: error("GEMINI_API_KEY is not set")
}
val openaiApiKey by lazy {
    System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is not set")
}

// somehow google's own API is quite sensitive
// content might be blocked with GCP api, but not with openrouter api

object Gemini3 {
    private fun builder() = GoogleAiGeminiChatModel.builder()
        .apiKey(geminiApiKey)
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
        .returnThinking(true)
        .sendThinking(true)
        .temperature(1.0)
        .topP(0.95)
        .topK(450)

    val flash by lazy {
        builder()
            .modelName("gemini-3-flash-preview")
            .build()
    }

    val pro by lazy {
        builder()
            .modelName("gemini-3-pro-preview")
            .build()
    }

    private fun batchBuilder() = GoogleAiGeminiBatchChatModel.builder()
        .apiKey(geminiApiKey)
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
        .returnThinking(true)
        .sendThinking(true)
        .temperature(1.0)
        .topP(0.95)
        .topK(450)

    val flashBatch by lazy {
        batchBuilder()
            .modelName("gemini-3-flash-preview")
            .build()
    }

    val proBatch by lazy {
        batchBuilder()
            .modelName("gemini-3-pro-preview")
            .build()
    }

    val embedding001 by lazy {
        GoogleAiEmbeddingModel.builder()
            .apiKey(geminiApiKey)
            .modelName("gemini-embedding-001")
            .build()
    }
}

val geminiEmbedding001TokenEstimator by lazy {
    GoogleAiGeminiTokenCountEstimator.builder()
        .apiKey(geminiApiKey)
        .modelName("gemini-embedding-001")
        .build()
}

val openRouter by lazy {
    OpenAiChatModel.builder()
        .apiKey(openaiApiKey)
        .baseUrl("https://openrouter.ai/api/v1")
        .modelName("google/gemini-3-pro-preview")
        .reasoningEffort("low")
        .temperature(1.0)
        .topP(0.95)
        .build()
}

val database = Database.connect(
    url = "jdbc:postgresql://localhost:5432/postgres",
    driver = "org.postgresql.Driver",
    user = "postgres",
    password = "postgres",
    dialect = PgVectorSqlDialect()
)