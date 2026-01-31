package info.skyblond.runs

import info.skyblond.db.Chunk
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import info.skyblond.database
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import java.io.File

/**
 * Slice the whole txt into chunks and save to database.
 *
 * Only need to run once.
 * */
fun main() {
    val document = database.sequenceOf(Documents).find { it.id eq 1 }!!

    val source = File(
        "/run/user/1000/gvfs/smb-share:server=100.99.241.120,share=data/txt/" +
                "終將成為妳 關於佐伯沙彌香 1 - 入間 人間.txt"
    ).readText()

    val blockSize = 1500
    val preContentSize = 500
    val postContentSize = 500

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

        println("==================== BEGIN CHUNK ====================")
        println(preContent)
        println("-------------------- PRE CONTENT ABOVE --------------------")
        println(content)
        println("-------------------- POST CONTENT BELOW --------------------")
        println(postContent)
        println("==================== END CHUNK ====================")

        val chunk = Chunk.Companion {
            this.indexOfDoc = index + 1 // start with 1
            this.preContent = preContent
            this.content = content
            this.postContent = postContent
            this.summary = "" // empty for now
            this.document = document
        }

        database.sequenceOf(Chunks).add(chunk)
    }

}