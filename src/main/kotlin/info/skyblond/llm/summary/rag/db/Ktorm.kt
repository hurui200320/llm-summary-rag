package info.skyblond.llm.summary.rag.db

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

interface ChunkSummaryVector : Entity<ChunkSummaryVector> {
    companion object : Entity.Factory<ChunkSummaryVector>()

    var chunk: Chunk
    var embedding: List<Float>
}

object ChunkSummaryVectors : Table<ChunkSummaryVector>("chunk_summary_vectors") {
    val chunkId = int("chunk_id").primaryKey()
        .references(Chunks) { it.chunk }
    val summary = vector("embedding").bindTo { it.embedding }
}

interface DocumentSummaryVector : Entity<DocumentSummaryVector> {
    companion object : Entity.Factory<DocumentSummaryVector>()

    var document: Document
    var embedding: List<Float>
}

object DocumentSummaryVectors : Table<DocumentSummaryVector>("document_summary_vectors") {
    val documentId = int("document_id").primaryKey()
        .references(Documents) { it.document }
    val summary = vector("embedding").bindTo { it.embedding }
}