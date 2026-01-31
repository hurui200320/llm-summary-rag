package info.skyblond

import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.tool.ToolErrorHandlerResult
import org.slf4j.LoggerFactory


fun main() {
    val logger = LoggerFactory.getLogger("Main")

    val model = OpenAiChatModel.builder()
        .apiKey(openaiApiKey)
        .baseUrl("https://openrouter.ai/api/v1")
        .modelName("openai/gpt-5.2")
        .reasoningEffort("high")
        .temperature(0.8)
        .topP(0.95)
        .build()

    val chatAgent = AiServices.builder(ChatAgent::class.java)
        .chatModel(model)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
        .systemMessageProvider { ChatAgent.systemPrompt }
        .tools(AgentTool)
        .hallucinatedToolNameStrategy {
            ToolExecutionResultMessage.from(
                it,
                "Error: tool ${it.name()} doesn't exist"
            )
        }
        .toolArgumentsErrorHandler { throwable, context ->
            logger.error("Failed to parse tool arguments", throwable)
            ToolErrorHandlerResult.text(
                "Failed to parse tool arguments: ${throwable.message}"
            )
        }
        .build()

    val response = chatAgent.chat("请问佐伯沙弥香中学时期是因为什么原因才有了自己的手机？")
    println(response)
}

interface ChatAgent {
    fun chat(input: String): String

    companion object {
        val systemPrompt = """
            |你是一个读书助手，能够根据用户提供的问题，从数据库中检索相关文档，并提供答案。
            |请务必调用工具来查询书籍或文档的内容，不要依靠本身的知识或幻觉回答用户。
        """.trimMargin()
    }
}