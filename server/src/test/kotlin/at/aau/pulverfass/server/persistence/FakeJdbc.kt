package at.aau.pulverfass.server.persistence

import java.io.PrintWriter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLFeatureNotSupportedException
import java.sql.Timestamp
import java.util.ArrayDeque
import java.util.logging.Logger
import javax.sql.DataSource

class FakeJdbcDataSource(
    connectionScripts: List<FakeConnectionScript>,
) : DataSource {
    private val remainingScripts = ArrayDeque(connectionScripts)
    var openedConnections: Int = 0
        private set

    override fun getConnection(): Connection {
        openedConnections += 1
        val script =
            remainingScripts.removeFirstIfPresent()
                ?: error("Keine Connection mehr konfiguriert.")
        return script.asConnection()
    }

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = getConnection()

    override fun getLogWriter(): PrintWriter? = null

    override fun setLogWriter(out: PrintWriter?) = Unit

    override fun setLoginTimeout(seconds: Int) = Unit

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()

    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

class FakeConnectionScript(
    statements: List<FakePreparedStatementScript>,
) {
    private val remainingStatements = ArrayDeque(statements)

    var autoCommit: Boolean = true
    var committed: Boolean = false
    var rolledBack: Boolean = false
    var closed: Boolean = false
    val preparedSql = mutableListOf<String>()

    fun asConnection(): Connection =
        proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args[0] as String
                    preparedSql += sql
                    val script =
                        remainingStatements.removeFirstIfPresent()
                            ?: error("Unerwartetes prepareStatement ohne Script für SQL: $sql")
                    script.assertSql(sql)
                    script.asPreparedStatement()
                }
                "getAutoCommit" -> autoCommit
                "setAutoCommit" -> {
                    autoCommit = args[0] as Boolean
                    Unit
                }
                "commit" -> {
                    committed = true
                    Unit
                }
                "rollback" -> {
                    rolledBack = true
                    Unit
                }
                "close" -> {
                    closed = true
                    Unit
                }
                "isClosed" -> closed
                "toString" -> "FakeConnection"
                else -> unsupported(method.name)
            }
        }
}

class FakePreparedStatementScript(
    private val expectedSqlFragment: String,
    private val rows: List<Map<Any, Any?>> = emptyList(),
    private val updateCount: Int = 0,
    private val onExecuteUpdate: ((Map<Int, Any?>) -> Int)? = null,
    private val failure: Exception? = null,
) {
    var executed: Boolean = false
        private set
    var closed: Boolean = false
        private set
    var lastParameters: Map<Int, Any?> = emptyMap()
        private set

    fun assertSql(sql: String) {
        val normalizedSql = sql.normalizeWhitespace()
        val normalizedExpected = expectedSqlFragment.normalizeWhitespace()
        check(normalizedSql.contains(normalizedExpected)) {
            "Erwartete SQL mit Fragment '$expectedSqlFragment', war aber '$sql'."
        }
    }

    fun asPreparedStatement(): PreparedStatement {
        val parameters = linkedMapOf<Int, Any?>()

        return proxy(PreparedStatement::class.java) { method, args ->
            when (method.name) {
                "setString", "setLong", "setInt", "setTimestamp", "setObject" -> {
                    parameters[args[0] as Int] = args[1]
                    Unit
                }
                "executeQuery" -> {
                    executed = true
                    lastParameters = parameters.toMap()
                    failure?.let { throw it }
                    FakeResultSetScript(rows).asResultSet()
                }
                "executeUpdate" -> {
                    executed = true
                    lastParameters = parameters.toMap()
                    failure?.let { throw it }
                    onExecuteUpdate?.invoke(lastParameters) ?: updateCount
                }
                "close" -> {
                    closed = true
                    Unit
                }
                "toString" -> "FakePreparedStatement"
                else -> unsupported(method.name)
            }
        }
    }
}

private class FakeResultSetScript(
    private val rows: List<Map<Any, Any?>>,
) {
    private var index = -1

    fun asResultSet(): ResultSet =
        proxy(ResultSet::class.java) { method, args ->
            when (method.name) {
                "next" -> {
                    index += 1
                    index < rows.size
                }
                "getString" -> currentRow().getColumn(args[0]) as String?
                "getLong" -> (currentRow().getColumn(args[0]) as Number).toLong()
                "getInt" -> (currentRow().getColumn(args[0]) as Number).toInt()
                "getTimestamp" -> currentRow().getColumn(args[0]) as Timestamp?
                "close" -> Unit
                "toString" -> "FakeResultSet"
                else -> unsupported(method.name)
            }
        }

    private fun currentRow(): Map<Any, Any?> =
        rows.getOrNull(index) ?: error("ResultSet steht auf keiner gültigen Zeile.")

    private fun Map<Any, Any?>.getColumn(key: Any?): Any? = this[key] ?: this[key.toString()]
}

private fun String.normalizeWhitespace(): String = trim().replace(Regex("\\s+"), " ")

private fun <T> ArrayDeque<T>.removeFirstIfPresent(): T? = if (isEmpty()) null else removeFirst()

private fun unsupported(methodName: String): Nothing =
    throw UnsupportedOperationException("Nicht unterstützter JDBC-Testaufruf: $methodName")

private fun <T> proxy(
    type: Class<T>,
    block: (method: java.lang.reflect.Method, args: Array<out Any?>) -> Any?,
): T =
    type.cast(
        Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
            InvocationHandler { _, method, args -> block(method, args ?: emptyArray()) },
        ),
    )
