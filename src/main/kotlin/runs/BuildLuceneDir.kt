package info.skyblond.runs

import info.skyblond.createAnalyzer
import info.skyblond.database
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import info.skyblond.luceneDir
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.IntField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.forEach
import org.ktorm.entity.sequenceOf


fun main() {
    val analyzer = createAnalyzer()
    val iwConfig = IndexWriterConfig(analyzer)
    // will remove old data
    iwConfig.openMode = IndexWriterConfig.OpenMode.CREATE
    IndexWriter(luceneDir, iwConfig).use { writer ->
        database.sequenceOf(Documents).forEach { document ->
            database.sequenceOf(Chunks)
                .filter { it.documentId eq document.id }
                .forEach { chunk ->
                    val doc = Document()

                    doc.add(IntField("chunkId", chunk.id, Field.Store.YES))
                    doc.add(IntField("documentId", document.id, Field.Store.YES))
                    doc.add(IntField("chunkIndex", chunk.indexOfDoc, Field.Store.YES))
                    doc.add(TextField("content", chunk.content, Field.Store.YES))

                    writer.addDocument(doc)
                }
        }
    }

}

