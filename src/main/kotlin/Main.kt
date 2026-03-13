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
        .apiKey(openRouterApiKey)
        .baseUrl("https://openrouter.ai/api/v1")
//        .modelName("openai/gpt-5.2")
//        .reasoningEffort("high")
//        .temperature(0.8)
        .modelName("google/gemini-3-pro-preview")
        .reasoningEffort("high")
        .temperature(1.0)
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

    val response = chatAgent.chat("""
        |在终将成为你的小说第三卷中，再与枝元阳挥手告别后，佐伯沙弥香内心想到“我像是被绳索牵引一般，回想起差点要忘记的老面孔”，
        |文中没有给出具体的答案，而下句则说“在课堂开始前的短暂时间，我这样想着枝元学妹与过去的事情。至少我不至于忘记她的名字和长相”。
        |她说想着过去的事情，至少不至于忘记名字和长相，我想不到一个确切地答案，我不知道此时此刻沙弥香想到了谁。
        |我觉得可能是或者小学时期游泳班上遇到的女孩，或者是七海灯子，但我不确定（但我能确定应该不是柚木学姐）。
        |我在犹豫会不会是游泳班的女孩，因为枝元阳的行为很像这个女孩，但是第一卷提及女孩的时候一直没有给出名字，
        |只是从始至终用“女孩”来指代这个角色。
    """.trimMargin())
    println(response)
}

interface ChatAgent {
    fun chat(input: String): String

    companion object {
        val systemPrompt = """
            |# Role
            |你是一个读书助手，能够根据用户提供的问题，从数据库中检索相关文档，并提供答案。
            |请务必通过调用工具来查询书籍或文档的内容，不要凭空捏造答案。
            |在回答用户问题时，你可以利用自身的知识和推理能力，但需要通过工具确认这是文档中存在的，而非你的幻觉。
            |
            |# Tools
            |本系统提供了一系列工具以便你在回答问题时查询已录入文档的内容。这些工具没有调用限制，你可随意使用。
            |数据库中的文档被切分成固定长度的Chunk，每个chunk具有在文档内的编号（index，从1开始），
            |并且每个chunk都有一个摘要，用于在不阅读较长原文的情况下快速了解该chunk的内容。
            |同时基于所有chunk的摘要，每个文档也有一个摘要，用于简要了解文档的内容。
            |系统配备了基于Apache Lucene的全文关键词检索工具（all match和any match），
            |同时针对摘要也配备了基于嵌入向量相似度的检索工具。
            |
            |通常情况下建议你首先列出所有文档的元数据，这会包含文档标题等简短信息。
            |随后根据用户的提问和你的分析，你可以查阅特定文档的摘要以便更详细的了解文档内容。
            |最后根据需求使用关键词或RAG检索特定文档或chunk，并按需调用对应的原文。
        """.trimMargin()
    }
}