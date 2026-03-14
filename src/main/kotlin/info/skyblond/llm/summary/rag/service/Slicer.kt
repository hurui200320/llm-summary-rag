package info.skyblond.llm.summary.rag.service

import info.skyblond.llm.summary.rag.database
import info.skyblond.llm.summary.rag.db.Chunk
import info.skyblond.llm.summary.rag.db.Chunks
import info.skyblond.llm.summary.rag.db.Document
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.any
import org.ktorm.entity.filter
import org.ktorm.entity.sequenceOf
import org.slf4j.LoggerFactory

object Slicer {
    private val logger = LoggerFactory.getLogger(Slicer::class.java)
    private val chunks = database.sequenceOf(Chunks)

    /**
     * Slice a document into chunks and save to the database.
     * */
    fun slice(
        document: Document, source: String,
        blockSize: Int, preContentSize: Int, postContentSize: Int,
    ) {
        if (chunks.filter { it.documentId eq document.id }.any()) {
            throw IllegalStateException("Document already sliced")
        }

        source.chunkedSequence(blockSize).forEachIndexed { index, content ->
            val startPos = index * blockSize
            val preContentStartPos = startPos - preContentSize
            val preContent = if (preContentStartPos >= 0) {
                source.substring(
                    preContentStartPos,
                    startPos.coerceAtMost(source.length)
                )
            } else ""
            val postContentStartPos = startPos + content.length
            val postContent = if (postContentStartPos < source.length) {
                source.substring(
                    startPos + content.length,
                    (startPos + content.length + postContentSize).coerceAtMost(source.length)
                )
            } else ""

            logger.info(
                """
                |Sliced chunk:
                |==================== BEGIN CHUNK ====================
                |${preContent}
                |-------------------- PRE CONTENT ABOVE --------------------
                |${content}
                |-------------------- POST CONTENT BELOW --------------------
                |${postContent}
                |==================== END CHUNK ====================
            """.trimMargin()
            )

            val chunk = Chunk.Companion {
                this.indexOfDoc = index + 1 // start with 1
                this.preContent = preContent
                this.content = content
                this.postContent = postContent
                this.summary = "" // empty for now
                this.document = document
            }

            chunks.add(chunk)
        }
    }
}