package info.skyblond

import com.google.genai.Client
import dev.langchain4j.model.openai.OpenAiChatModel
import info.skyblond.db.Chunks
import info.skyblond.db.Document
import info.skyblond.db.Documents
import info.skyblond.service.EmbeddingGenerator
import info.skyblond.service.Lucene
import info.skyblond.service.Slicer
import info.skyblond.service.Summarizer
import org.ktorm.dsl.eq
import org.ktorm.dsl.neq
import org.ktorm.entity.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture


fun main() {
    val document = Document {
        title = "終將成為妳 關於佐伯沙彌香 3"
        author = "入間 人間"
        language = "繁体中文"
        summary = ""
    }
    val content = File(
        "/run/user/1000/gvfs/smb-share:server=100.99.241.120,share=data/txt/" +
                "終將成為妳 關於佐伯沙彌香 3 - 入間 人間.txt"
    ).readText()

    val blockSize = 1500
    val preContentSize = 500
    val postContentSize = 500

    val chunkSummaryLength = 500
    val documentSummaryLength = 1000


    val summaryBackend = OpenAiChatModel.builder()
        .apiKey(openRouterApiKey)
        .baseUrl("https://openrouter.ai/api/v1")
        .modelName("openai/gpt-5.1")
        .reasoningEffort("high")
        .temperature(0.4)
        .topP(0.95)
        .build()

    val geminiClient = Client.builder().apiKey(geminiApiKey).build()


    val logger = LoggerFactory.getLogger("IngestDocument")

    logger.info("Checking database...")
    val documents = database.sequenceOf(Documents)
    val chunks = database.sequenceOf(Chunks)
    documents.find { it.title eq document.title }?.let {
        throw IllegalArgumentException("Document with same title already exists")
    }
    documents.add(document)
    logger.info("Document added: id=${document.id}")

    logger.info("Slicing document...")
    Slicer.slice(document, content, blockSize, preContentSize, postContentSize)

    logger.info("Generating chunk summary...")
    chunks
        .filter { it.documentId eq document.id }
        .filter { it.summary eq "" }
        .sortedBy { it.indexOfDoc }
        .map {
            CompletableFuture.runAsync {
                Summarizer.summarizeChunk(summaryBackend, it, chunkSummaryLength)
            }
        }.forEach { it.join() }

    logger.info("Generating document summary...")
    Summarizer.summarizeDocument(summaryBackend, document, documentSummaryLength)

    logger.info("Generating chunk summary embedding vectors...")
    chunks
        .filter { it.documentId eq document.id }
        .filter { it.summary neq "" }
        .sortedBy { it.indexOfDoc }
        .forEach { chunk ->
            EmbeddingGenerator.summaryEmbedding(geminiClient, chunk)
        }

    logger.info("Generating document summary embedding vectors...")
    EmbeddingGenerator.summaryEmbedding(geminiClient, document)

    logger.info("Building Lucene index...")
    Lucene.buildIndex(luceneDir)
}

