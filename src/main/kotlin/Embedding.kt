package info.skyblond

import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import kotlin.collections.first
import kotlin.math.sqrt

fun documentEmbedding(client: Client, content: String, title: String): List<Float> {
    val resp = client.models.embedContent(
        "gemini-embedding-001",
        content,
        EmbedContentConfig.builder()
            .taskType("RETRIEVAL_DOCUMENT")
            .outputDimensionality(1536)
            .title(title)
            .build()
    )
    val embedding = resp.embeddings().get().first()
    val value = embedding.values().get()
    return value.normalize()
}

fun queryEmbedding(client: Client, query: String): List<Float> {
    val resp = client.models.embedContent(
        "gemini-embedding-001",
        query,
        EmbedContentConfig.builder()
            .taskType("RETRIEVAL_QUERY")
            .outputDimensionality(1536)
            .build()
    )
    val embedding = resp.embeddings().get().first()
    val value = embedding.values().get()
    return value.normalize()
}

fun List<Float>.l2norm(): Float = sqrt(this.map { it * it }.sum())

fun List<Float>.normalize(): List<Float> {
    val norm = this.l2norm()
    if (norm == 0.0f) return this
    return this.map { it / norm }
}

fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    require(a.size == b.size) { "Vectors must have the same dimension" }

    var dotProduct = 0f
    var normA = 0f
    var normB = 0f

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    return dotProduct / (sqrt(normA) * sqrt(normB))
}

fun List<Float>.cosineDistance(that: List<Float>): Float {
    return 1f - cosineSimilarity(this, that)
}