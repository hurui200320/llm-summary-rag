package info.skyblond

import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import info.skyblond.db.Chunks
import info.skyblond.db.Document
import info.skyblond.db.Documents
import info.skyblond.service.EmbeddingGenerator
import info.skyblond.service.Lucene
import info.skyblond.service.Slicer
import info.skyblond.service.Summarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ktorm.dsl.eq
import org.ktorm.dsl.neq
import org.ktorm.entity.*
import org.slf4j.LoggerFactory
import java.io.File


fun main() = runBlocking(Dispatchers.IO) {
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

    val summaryBackend = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openai/gpt-5.1",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Completion,
        ),
        contextLength = 400_000,
        maxOutputTokens = 128_000
    )
    val summaryParameters = OpenRouterParams(
        temperature = 0.4,
        topP = 0.95,
        additionalProperties = mapOf(
            "reasoning" to JsonObject(mapOf(
                "effort" to JsonPrimitive("high")
            ))
        )
    )

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
            async {
                Summarizer.summarizeChunk(summaryBackend, summaryParameters, it, chunkSummaryLength)
            }
        }.joinAll()

    logger.info("Generating document summary...")
    Summarizer.summarizeDocument(summaryBackend, summaryParameters, document, documentSummaryLength)

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

