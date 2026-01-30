package info.skyblond

import org.ktorm.entity.Entity
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.text

interface Document : Entity<Document> {
    companion object : Entity.Factory<Document>()

    val id: Int
    var title: String
    var author: String
    var language: String
    var summary: String
}

object Documents : Table<Document>("documents") {
    val id = int("id").primaryKey().bindTo { it.id }
    var title = text("title").bindTo { it.title }
    var author = text("author").bindTo { it.author }
    var language = text("language").bindTo { it.language }
    var summary = text("summary").bindTo { it.summary }
}

interface Chunk : Entity<Chunk> {
    companion object : Entity.Factory<Chunk>()

    val id: Int
    var indexOfDoc: Int
    var preContent: String
    var content: String
    var postContent: String
    var summary: String

    var document: Document
}

object Chunks : Table<Chunk>("chunks") {
    val id = int("id").primaryKey().bindTo { it.id }
    val indexOfDoc = int("index_of_doc").bindTo { it.indexOfDoc }
    val preContent = text("pre_content").bindTo { it.preContent }
    val content = text("content").bindTo { it.content }
    val postContent = text("post_content").bindTo { it.postContent }
    val summary = text("summary").bindTo { it.summary }

    val documentId = int("document_id").references(Documents) { it.document }
}