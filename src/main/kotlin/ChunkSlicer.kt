package info.skyblond

import dev.langchain4j.service.MemoryId

interface ChunkSlicer {
    /**
     * Input a text and return the tail of the chunk.
     * */
    fun slice(text: String): String

    companion object {
        @JvmStatic
        fun systemMessage(@MemoryId memoryId: Any?): String {
            return """
                |You're part of a AI powered summarization system.
                |It can cut a blob input into meaningful chunks and summarize it.
                |
                |## Goal
                |
                |Your goal is to decide where to cut the text to get the first chunk of the input.
                |The decided chunk will be then fed into an embedding model to generate vector for RAG system.
                |
                |+ Keep chunks short, usually a chunk for RAG system is about 500 tokens.
                |+ Do NOT include truncated sentence in the chunk.
                |+ The content in one chunk should be closely related, so the result embedding vector is meaningful.
                |+ Prefer a small chunk where the content describes only one topic/thing/story/scene.
                |
                |## Output
                |
                |Only output the tail content (usually a sentence) before the cutting point.
                |
                |To ensure the external system can make the cut at the correct point,
                |you must ensure the tail you selected is unique.
                |You may decide how long the tail you need to output.
                |You must output the precise tail of the chunk.
            """.trimMargin()
        }

    }
}