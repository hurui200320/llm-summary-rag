package info.skyblond.llm.summary.rag.db

import org.ktorm.database.Database
import org.ktorm.expression.ScalarExpression
import org.ktorm.expression.SqlExpression
import org.ktorm.expression.SqlFormatter
import org.ktorm.schema.*
import org.ktorm.support.postgresql.PostgreSqlDialect
import org.ktorm.support.postgresql.PostgreSqlFormatter
import org.postgresql.util.PGobject
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * PGVector type for ktorm.
 */
object VectorSqlType : SqlType<List<Float>>(Types.OTHER, "vector") {

    override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: List<Float>) {
        // list to string (pgvector format)
        val vectorString = parameter.joinToString(separator = ",", prefix = "[", postfix = "]")

        // create PG object
        val pgObject = PGobject()
        pgObject.type = "vector"
        pgObject.value = vectorString

        ps.setObject(index, pgObject)
    }

    override fun doGetResult(rs: ResultSet, index: Int): List<Float>? {
        val str = rs.getString(index)
        if (str.isNullOrBlank()) return null

        return str.trim('[', ']')
            .split(",")
            .map { it.trim().toFloat() }
    }
}

fun BaseTable<*>.vector(name: String): Column<List<Float>> {
    return registerColumn(name, VectorSqlType)
}

/**
 * Cosine distance operator for PGVector.
 * Returns the cosine distance between two vectors.
 * Usage: column.cosineDistance(targetVector)
 */
infix fun ColumnDeclaring<List<Float>>.cosineDistance(vector: List<Float>): ScalarExpression<Float> {
    return CosineDistanceExpression(this.asExpression(), vector)
}

private class CosineDistanceExpression(
    val left: ScalarExpression<List<Float>>,
    val right: List<Float>,
    override val sqlType: SqlType<Float> = FloatSqlType,
    override val isLeafNode: Boolean = false,
    override val extraProperties: Map<String, Any> = mapOf()
) : ScalarExpression<Float>()


open class PgVectorSqlDialect : PostgreSqlDialect() {

    override fun createSqlFormatter(
        database: Database, beautifySql: Boolean, indentSize: Int
    ): SqlFormatter {
        return PgVectorSqlFormatter(database, beautifySql, indentSize)
    }
}

class PgVectorSqlFormatter(
    database: Database, beautifySql: Boolean, indentSize: Int
) : PostgreSqlFormatter(database, beautifySql, indentSize) {

    override fun visitUnknown(expr: SqlExpression): SqlExpression {
        return when (expr) {
            is CosineDistanceExpression -> {
                visit(expr.left)

                write(" <=> ")

                write("\'")
                write(expr.right.joinToString(separator = ",", prefix = "[", postfix = "]"))
                write("\' ")

                expr
            }

            else -> {
                super.visitUnknown(expr)
            }
        }
    }
}