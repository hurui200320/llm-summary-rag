package info.skyblond

import com.google.genai.Client
import info.skyblond.db.PgVectorSqlDialect
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.ktorm.database.Database
import java.nio.file.Paths
import java.io.File

private fun loadEnvFile(envFile: File = File(".env")): Map<String, String> {
    if (!envFile.exists()) return emptyMap()
    
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index == -1) null
            else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }
        .toMap()
}

private val envMap by lazy { loadEnvFile() }

fun getEnv(key: String): String? {
    return System.getenv(key) ?: envMap[key]
}

val geminiApiKey by lazy {
    getEnv("GEMINI_API_KEY") ?: error("GEMINI_API_KEY is not set")
}
val openRouterApiKey by lazy {
    getEnv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY is not set")
}

val geminiClient: Client by lazy { Client.builder().apiKey(geminiApiKey).build() }

val database = Database.connect(
    url = "jdbc:postgresql://localhost:5432/postgres",
    driver = "org.postgresql.Driver",
    user = "postgres",
    password = "postgres",
    dialect = PgVectorSqlDialect()
)

val luceneDir: Directory = FSDirectory.open(Paths.get("./lucene-index"))
