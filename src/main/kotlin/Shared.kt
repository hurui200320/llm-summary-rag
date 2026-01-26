package info.skyblond

import dev.langchain4j.model.googleai.*
import dev.langchain4j.model.openai.OpenAiChatModel

val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: error("GEMINI_API_KEY is not set")
val openaiApiKey = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is not set")

// somehow google's own API is quite sensitive
// content might be blocked with GCP api, but not with openrouter api

val gemini3flash = GoogleAiGeminiChatModel.builder()
    .apiKey(geminiApiKey)
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

val gemini3flashBatch = GoogleAiGeminiBatchChatModel.builder()
    .apiKey(geminiApiKey)
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

val geminiEmbedding001TokenEstimator = GoogleAiGeminiTokenCountEstimator.builder()
    .apiKey(geminiApiKey)
    .modelName("gemini-embedding-001")
    .build()

val openRouter = OpenAiChatModel.builder()
    .apiKey(openaiApiKey)
    .baseUrl("https://openrouter.ai/api/v1")
    .modelName("google/gemini-3-pro-preview")
    .reasoningEffort("low")
    .temperature(1.0)
    .topP(0.95)
    .build()