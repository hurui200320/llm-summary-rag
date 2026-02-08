package info.skyblond.service

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.output.FinishReason
import info.skyblond.database
import info.skyblond.db.Chunk
import info.skyblond.db.Chunks
import info.skyblond.db.Document
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.joinToString
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.sortedBy
import org.slf4j.LoggerFactory

object Summarizer {
    private val logger = LoggerFactory.getLogger(Summarizer::class.java)
    private val chunks = database.sequenceOf(Chunks)

    /**
     * Summary a [chunk] using a given [model].
     *
     * The [chunk] must be pulled from the actual database.
     *
     * @throws IllegalStateException if summary failed (model finish reason is not STOP)
     * */
    fun summarizeChunk(model: ChatModel, chunk: Chunk, summaryLength: Int) {
        val chatReq = ChatRequest.builder()
            .messages(
                SystemMessage(createChunkSummarySystemPrompt(summaryLength)),
                UserMessage(
                    """
                        |<pre_context>
                        |${preprocessXML(chunk.preContent)}
                        |</pre_context>
                        |<content_to_summary>
                        |${preprocessXML(chunk.content)}
                        |</content_to_summary>
                        |<post_context>
                        |${preprocessXML(chunk.postContent)}
                        |</post_context>
                    """.trimMargin()
                )
            )
            .build()
        val resp = model.chat(chatReq)
        if (resp.finishReason() != FinishReason.STOP) {
            throw IllegalStateException(
                "Summary failed for chunk #${chunk.indexOfDoc} in doc #${chunk.document.id}" +
                        "(${chunk.document.title}). Finish reason: ${resp.finishReason()}"
            )
        }
        logger.info("Summary for chunk #${chunk.indexOfDoc}: \n${resp.aiMessage().text()}")
        chunk.summary = resp.aiMessage().text()
        chunk.flushChanges()
    }


    /**
     * Summary a [document] using a given [model].
     *
     * The [document] must be pulled from the actual database.
     *
     * @throws IllegalStateException if summary failed (model finish reason is not STOP)
     * */
    fun summarizeDocument(
        model: ChatModel,
        document: Document,
        summaryLength: Int
    ) {
        val chunks = chunks
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

        val resp = model.chat(chatRequest)
        if (resp.finishReason() != FinishReason.STOP) {
            throw IllegalStateException(
                "Summary failed for doc #${document.id}" +
                        "(${document.title}). Finish reason: ${resp.finishReason()}"
            )
        }

        logger.info("Summary for doc #${document.id}: \n${resp.aiMessage().text()}")
        document.summary = resp.aiMessage().text()
        document.flushChanges()
    }

    /**
     * Use CDATA to wrap content if there are characters need to escape.
     * */
    private fun preprocessXML(text: String): String {
        if (text.contains("<") || text.contains(">")) {
            val safeText = text.replace("]]>", "]]]]><![CDATA[>")
            return "<![CDATA[${safeText}]]>"
        }
        return text
    }

    /**
     * Generate a system prompt for chunk summary chunk, targeting [summaryLength] chars of output.
     * */
    private fun createChunkSummarySystemPrompt(summaryLength: Int) = """
        |# Role
        |你是一位专业的中文小说摘要专家。
        |你擅长从长篇叙事中精准提取核心情节、关键冲突和人物关系，同时具备极强的上下文理解能力。
        |
        |# Task
        |你将接收到一段被 XML 标签包裹的小说文本。你的唯一任务是：**仅对 <content_to_summary> 标签内的文本进行摘要。**
        |
        |# Input Structure
        |输入包含三个部分（部分内容可能包裹在 CDATA 中）：
        |1. <pre_context>: 前文缓冲（用于补充因切分导致的断句和指代不清）。
        |2. <content_to_summary>: **目标文本**（这是你唯一需要摘要的内容）。
        |3. <post_context>: 下文缓冲（用于补全目标文本末尾被切断的句子）。
        |
        |# Strategies & Guidelines
        |在生成摘要前，请遵循以下思维步骤：
        |
        |1. **边界识别**：明确 <content_to_summary> 的起始和结束点。
        |2. **上下文融合 (Context Stitching)**：
        |   - 查询 <pre_context> 来判断 <content_to_summary> 的开头是否被切断，如果是，请根据 <pre_context> 将 <content_to_summary> 的开头拼接完整。 
        |   - 如果 <content_to_summary> 中出现了“他/她/它/我”等通用称呼或指代不明，请查询 <pre_context> 确定具体角色名（使用全名）。
        |   - 查询 <post_context> 来判断 <content_to_summary> 的结尾是否被切断，如果是，请根据 <post_context> 补全 <content_to_summary> 的结尾，**但不要总结 <post_context> 中发生的新事件。**
        |3. **噪音过滤**：
        |   - 严格排除 <pre_context> 中已经发生过的事件。
        |   - 严格排除 <post_context> 中即将发生的事件。
        |   - **重点**：如果 <content_to_summary> 中的某句话只是前文的重复说明，请忽略。
        |
        |# Constraints
        |- **严格性**：绝对不要将 <pre_context> 或 <post_context> 里的独立情节写入摘要。摘要必须严格限制在目标块的时间范围内。
        |- **准确性**：保留关键的人名、地名和特定道具名称，不要使用模糊的代词（如“有人”或“他”）。
        |- **客观性**：直接陈述事实，不要添加“这段文字描述了”、“作者写道”等元描述。
        |- **长度**：将摘要控制在 $summaryLength 字以内，在不超出长度的前提下尽量保留细节。
        |
        |# Output Format
        |直接输出摘要内容，不要包含任何 XML 标签、Markdown 标题或开场白。
        |摘要内容必须只有一行，不得换行。
    """.trimMargin()

    /**
     * Generate a system prompt for book summary chunk, targeting [summaryLength] chars of output.
     * */
    private fun createBookSummarySystemPrompt(summaryLength: Int) = """
        |# Role
        |你是一位文学编辑。
        |你将看到用户提供一份长篇小说的**分块摘要列表**，按文本先后顺序排序。
        |
        |# Task
        |基于用户提供的分块摘要列表，重构并撰写一份连贯、有深度的**全书故事梗概**。
        |
        |# Strategies & Guidelines
        |在生成摘要前，请遵循以下思维步骤：
        |
        |1. **去碎片化 (De-fragmentation)**：不要像记流水账一样罗列（“首先...然后...接着...”）。请将情节融合成流畅的叙事流。
        |2. **抓主线**：识别故事的核心冲突（起因）、高潮（转折）和结局。忽略过于琐碎的支线。
        |3. **人物弧光**：在摘要中体现主角（们）的性格变化或成长轨迹。
        |4. **结构**：
        |   - 第一部分：世界观与开端。
        |   - 第二部分：主要冲突与发展。
        |   - 第三部分：高潮与结局。
        |
        |# Output Format
        |- **长度**：将摘要控制在 $summaryLength 字左右（或根据你的具体需求调整，浮动不超过50字）。
        |- **段落**：根据小说故事情节选择段落数量，力求紧凑。
        |- 直接输出摘要内容，不要包含任何 XML 标签、Markdown 标题或开场白。
    """.trimMargin()
}