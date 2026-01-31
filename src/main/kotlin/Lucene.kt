package info.skyblond

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.LowerCaseFilterFactory
import org.apache.lucene.analysis.custom.CustomAnalyzer
import org.apache.lucene.analysis.icu.ICUTransformFilterFactory
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerFactory
import org.apache.lucene.analysis.ngram.NGramFilterFactory

fun createAnalyzer(): Analyzer = CustomAnalyzer.builder()
    .withTokenizer(ICUTokenizerFactory::class.java)
    .addTokenFilter( // Convert simplified Chinese to Traditional Chinese
        ICUTransformFilterFactory::class.java, mutableMapOf(
            "id" to "Simplified-Traditional"
        )
    )
    // turn english to lowercase
    .addTokenFilter(LowerCaseFilterFactory::class.java)
    .addTokenFilter( // N-gram tokenizer
        NGramFilterFactory::class.java, mutableMapOf(
            // min size = 2 means it can't search a single Chinese character
            "minGramSize" to "2",
            // max 3, good enough
            "maxGramSize" to "3"
        )
    )
    .build()