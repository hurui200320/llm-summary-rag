package info.skyblond.llm.summary.rag.koog

import ai.koog.prompt.executor.clients.openai.base.models.*
import ai.koog.prompt.executor.clients.openrouter.models.ProviderPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class OpenRouterChatCompletionRequest(
    val messages: List<OpenAIMessage> = emptyList(),
    val prompt: String? = null,
    override val model: String? = null,
    override val stream: Boolean? = null,
    override val temperature: Double? = null,
    val tools: List<OpenAITool>? = null,
    val toolChoice: OpenAIToolChoice? = null,
    override val topP: Double? = null,
    override val topLogprobs: Int? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val stop: List<String>? = null,
    val logprobs: Boolean? = null,
    val seed: Int? = null,
    val topK: Int? = null,
    val repetitionPenalty: Double? = null,
    val logitBias: Map<Int, Double>? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val prediction: OpenAIStaticContent? = null,
    val transforms: List<String>? = null,
    val models: List<String>? = null,
    val route: String? = null,
    val provider: ProviderPreferences? = null,
    val user: String? = null,
    val reasoning: OpenRouterReasoning? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

@Serializable
class OpenRouterReasoning(
    val effort: String? = null,
    val enabled: Boolean? = null,
    val maxTokens: Int? = null,
    val summary: String? = null,
)