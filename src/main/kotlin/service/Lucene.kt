package info.skyblond.service

import info.skyblond.database
import info.skyblond.db.Chunks
import info.skyblond.db.Documents
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.cjk.CJKBigramFilterFactory
import org.apache.lucene.analysis.core.LowerCaseFilterFactory
import org.apache.lucene.analysis.custom.CustomAnalyzer
import org.apache.lucene.analysis.icu.ICUTransformFilterFactory
import org.apache.lucene.analysis.standard.StandardTokenizerFactory
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.IntField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.Directory
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.forEach
import org.ktorm.entity.sequenceOf

object Lucene {
    private val chunks = database.sequenceOf(Chunks)
    private val documents = database.sequenceOf(Documents)

    private val analyzer = createAnalyzer()
    private val queryParser = QueryParser("content", analyzer).apply {
        // require all match due to N gram tokenization
        defaultOperator = QueryParser.Operator.AND
    }

    /**
     * Build and write (overwrite) index to the given [dir].
     * */
    fun buildIndex(dir: Directory) {
        val iwConfig = IndexWriterConfig(analyzer)
        // will remove old data
        iwConfig.openMode = IndexWriterConfig.OpenMode.CREATE
        IndexWriter(dir, iwConfig).use { writer ->
            documents.forEach { document ->
                chunks
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

    /**
     * Search the index, return chunks that have any match with the [keywords].
     * */
    fun searchIndexAnyMatch(
        keywords: List<String>,
        searcher: IndexSearcher,
        n: Int
    ): TopDocs {
        val queryBuilder = BooleanQuery.Builder()
        keywords.forEach { word ->
            // due to N-gram, we need to match all sub pairs in a keyword
            // so the parser should still use AND operator
            // but to allow OR, we use sub query:
            // (AB and BC and CD) OR (EF and FG and GH)
            // so this will match either ABCD or EFGH
            queryBuilder.add(
                queryParser.parse(word),
                BooleanClause.Occur.SHOULD // essentially OR
            )
        }
        val luceneQuery = queryBuilder.build()
        return searcher.search(luceneQuery, n)
    }

    /**
     * Search the index, return chunks that match all the [keywords].
     * */
    fun searchIndexAllMatch(
        keywords: List<String>,
        searcher: IndexSearcher,
        n: Int
    ): TopDocs {
        val queryBuilder = BooleanQuery.Builder()
        keywords.forEach { word ->
            queryBuilder.add(
                queryParser.parse(word),
                BooleanClause.Occur.MUST // essentially AND
            )
        }
        val luceneQuery = queryBuilder.build()
        return searcher.search(luceneQuery, n)
    }

    private fun createAnalyzer(): Analyzer = CustomAnalyzer.builder()
        .withTokenizer(StandardTokenizerFactory::class.java)
        .addTokenFilter(
            // Convert Traditional Chinese to simplified Chinese
            // Mainly because one simplified Chinese character can be represented by multiple traditional Chinese characters,
            // but one traditional Chinese character can be represented by one simplified Chinese character
            ICUTransformFilterFactory::class.java, mutableMapOf(
                "id" to "Traditional-Simplified"
            )
        )
        // turn English to lowercase
        .addTokenFilter(LowerCaseFilterFactory::class.java)
        .addTokenFilter( // CJK Bigram, better than N gram
            CJKBigramFilterFactory::class.java, mutableMapOf(
                // keep the single character
                "outputUnigrams" to "true"
            )
        )
        .build()
}