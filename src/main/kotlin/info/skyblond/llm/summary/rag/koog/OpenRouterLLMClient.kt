package info.skyblond.llm.summary.rag.koog

import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.modelsById
import ai.koog.prompt.executor.clients.openai.base.AbstractOpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openai.base.models.*
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionResponse
import ai.koog.prompt.executor.clients.openrouter.models.OpenRouterChatCompletionStreamResponse
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.*
import kotlin.time.Clock

class OpenRouterLLMClient(
    apiKey: String,
    private val settings: OpenRouterClientSettings = OpenRouterClientSettings(),
    baseClient: HttpClient = HttpClient(),
    clock: Clock = Clock.System,
    toolsConverter: OpenAICompatibleToolDescriptorSchemaGenerator = OpenAICompatibleToolDescriptorSchemaGenerator()
) : AbstractOpenAILLMClient<OpenRouterChatCompletionResponse, OpenRouterChatCompletionStreamResponse>(
    apiKey = apiKey,
    settings = settings,
    baseClient = baseClient,
    clock = clock,
    logger = staticLogger,
    toolsConverter = toolsConverter
) {

    private companion object {
        private val staticLogger = KotlinLogging.logger { }

        init {
            // On class load register custom OpenAI JSON schema generators for structured output.
            registerOpenAIJsonSchemaGenerators(LLMProvider.OpenRouter)
        }
    }

    /**
     * Returns the specific implementation of the `LLMProvider` associated with this client.
     *
     * In this case, it identifies the `OpenRouter` provider as the designated LLM provider
     * for the client.
     *
     * @return The `LLMProvider` instance representing OpenRouter.
     */
    override fun llmProvider(): LLMProvider = LLMProvider.OpenRouter

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean
    ): String {
        val openRouterParams: OpenRouterParams = params.toOpenRouterParams()
        val responseFormat = createResponseFormat(params.schema, model)

        val request = OpenRouterChatCompletionRequest(
            messages = messages,
            model = model.id,
            stream = stream,
            temperature = openRouterParams.temperature,
            tools = tools,
            toolChoice = openRouterParams.toolChoice?.toOpenAIToolChoice(),
            topP = openRouterParams.topP,
            topLogprobs = openRouterParams.topLogprobs,
            maxTokens = openRouterParams.maxTokens,
            frequencyPenalty = openRouterParams.frequencyPenalty,
            presencePenalty = openRouterParams.presencePenalty,
            responseFormat = responseFormat,
            stop = openRouterParams.stop,
            logprobs = openRouterParams.logprobs,
            topK = openRouterParams.topK,
            repetitionPenalty = openRouterParams.repetitionPenalty,
            minP = openRouterParams.minP,
            topA = openRouterParams.topA,
            prediction = openRouterParams.speculation?.let { OpenAIStaticContent(Content.Text(it)) },
            transforms = openRouterParams.transforms,
            models = openRouterParams.models,
            route = openRouterParams.route,
            provider = openRouterParams.provider,
            user = openRouterParams.user,
            reasoning = openRouterParams.additionalProperties?.get("reasoning")?.let {
                val obj = it.jsonObject
                OpenRouterReasoning(
                    effort = obj["effort"]?.jsonPrimitive?.contentOrNull,
                    enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull,
                    maxTokens = obj["max_tokens"]?.jsonPrimitive?.intOrNull,
                    summary = obj["summary"]?.jsonPrimitive?.contentOrNull,
                )
            },
            additionalProperties = openRouterParams.additionalProperties,
        )

        return json.encodeToString(OpenRouterChatCompletionRequestSerializer(), request)
    }

    override fun processProviderChatResponse(response: OpenRouterChatCompletionResponse): List<LLMChoice> {
        // Handle error responses
        response.error?.let { error ->
            throw LLMClientException(
                clientName = clientName,
                message = "OpenRouter API error: ${error.message}${error.type?.let { " (type: $it)" } ?: ""}${error.code?.let { " (code: $it)" } ?: ""}",
                cause = null
            )
        }

        require(response.choices.isNotEmpty()) { "Empty choices in response" }
        return response.choices.map {
            it.message.toMessageResponses(
                it.finishReason,
                createMetaInfo(response.usage),
            )
        }
    }

    override fun decodeStreamingResponse(data: String): OpenRouterChatCompletionStreamResponse =
        json.decodeFromString(data)

    override fun decodeResponse(data: String): OpenRouterChatCompletionResponse =
        json.decodeFromString(data)

    override fun processStreamingResponse(
        response: Flow<OpenRouterChatCompletionStreamResponse>
    ): Flow<StreamFrame> = buildStreamFrameFlow {
        var finishReason: String? = null
        var metaInfo: ResponseMetaInfo? = null

        response.collect { chunk ->
            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta.content?.let { emitTextDelta(it) }

                choice.delta.toolCalls?.forEachIndexed { index, openAIToolCall ->
                    val id = openAIToolCall.id
                    val name = openAIToolCall.function.name
                    val arguments = openAIToolCall.function.arguments
                    emitToolCallDelta(id, name, arguments, index)
                }

                choice.finishReason?.let { finishReason = it }
            }

            chunk.usage?.let { metaInfo = createMetaInfo(chunk.usage) }
        }

        emitEnd(finishReason, metaInfo)
    }

    public override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        logger.warn { "Moderation is not supported by OpenRouter API" }
        throw UnsupportedOperationException("Moderation is not supported by OpenRouter API.")
    }

    /**
     * Fetches the list of available models from the OpenRouter service.
     * https://openrouter.ai/docs/api/api-reference/models/get-models
     *
     * @return A list of model IDs available from OpenRouter.
     */
    override suspend fun models(): List<LLModel> {
        logger.debug { "Fetching available models from OpenRouter" }
        val models = httpClient.get(
            path = settings.modelsPath,
            responseType = OpenRouterModelsResponse::class
        )

        val modelsById = OpenRouterModels.modelsById()
        return models.data.map {
            modelsById[it.id] ?: LLModel(
                provider = llmProvider(),
                id = it.id
            )
        }
    }
}

fun LLMParams.toOpenRouterParams(): OpenRouterParams {
    if (this is OpenRouterParams) return this
    return OpenRouterParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        additionalProperties = additionalProperties,
    )
}

internal class OpenRouterChatCompletionRequestSerializer(
    tSerializer: KSerializer<OpenRouterChatCompletionRequest> = OpenRouterChatCompletionRequest.serializer()
) : JsonTransformingSerializer<OpenRouterChatCompletionRequest>(tSerializer) {

    private val additionalPropertiesField = "additional_properties"

    private val knownProperties = tSerializer.descriptor.elementNames

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject

        return buildJsonObject {
            // Add all properties except additionalProperties
            obj.entries.asSequence()
                .filterNot { (key, _) -> key == additionalPropertiesField }
                .forEach { (key, value) -> put(key, value) }

            // Merge additional properties into the root level (avoiding conflicts)
            obj[additionalPropertiesField]?.jsonObject?.entries
                ?.filterNot { (key, _) -> obj.containsKey(key) }
                ?.forEach { (key, value) -> put(key, value) }
        }
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val (known, additional) = obj.entries.partition { (key, _) -> key in knownProperties }

        return buildJsonObject {
            // Add known properties efficiently
            known.forEach { (key, value) -> put(key, value) }

            // Group additional properties under an additionalProperties key if any exist
            if (additional.isNotEmpty()) {
                put(
                    additionalPropertiesField,
                    buildJsonObject {
                        additional.forEach { (key, value) -> put(key, value) }
                    }
                )
            }
        }
    }
}

