package info.skyblond

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.model.TokenCountEstimator

class EmbeddingTokenCountEstimator(
    private val estimator: TokenCountEstimator
) {
    @Tool("Estimate the token count for embedding model")
    fun estimateTokenCountForEmbeddingModel(input: String): Int =
        this.estimator.estimateTokenCountInText(input)
}