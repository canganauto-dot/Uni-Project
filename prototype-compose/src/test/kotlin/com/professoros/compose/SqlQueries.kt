package com.professoros.compose

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.*
import java.util.Collections
import java.util.Properties
import java.util.logging.Logger

/** Test-only JDBC interception. Counts executed SELECTs, excluding PRAGMAs and transaction control. */
internal class SqlQueries(path: Path) : Driver, AutoCloseable {
    private val url = "jdbc:sqlite:" + path.toAbsolutePath().normalize()
    private val original = DriverManager.getDriver(url)
    val selects: MutableList<String> = Collections.synchronizedList(mutableListOf())
    var afterSelect: ((String) -> Unit)? = null

    init {
        DriverManager.deregisterDriver(original)
        DriverManager.registerDriver(this)
    }

    override fun connect(value: String, properties: Properties): Connection? {
        val connection = original.connect(value, properties) ?: return null
        if (value != url) return connection
        return proxy(Connection::class.java, connection) { method, args, result ->
            when (method) {
                "prepareStatement" -> statement(result as PreparedStatement, args[0] as String)
                "createStatement" -> proxy(Statement::class.java, result as Statement) { name, values, answer ->
                    if (name.startsWith("execute") && values.firstOrNull() is String) record(values[0] as String)
                    answer
                }
                else -> result
            }
        }
    }

    private fun statement(statement: PreparedStatement, sql: String) =
        proxy(PreparedStatement::class.java, statement) { name, _, result ->
            if (name.startsWith("execute")) record(sql)
            result
        }

    private fun record(sql: String) {
        if (sql.trimStart().startsWith("SELECT", ignoreCase = true)) {
            selects.add(sql)
            afterSelect?.invoke(sql)
        }
    }

    private fun <T> proxy(type: Class<T>, target: T, after: (String, Array<out Any?>, Any?) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments ->
            val args = arguments ?: emptyArray()
            try { after(method.name, args, method.invoke(target, *args)) }
            catch (failure: InvocationTargetException) { throw failure.cause ?: failure }
        })

    override fun acceptsURL(value: String) = original.acceptsURL(value)
    override fun getPropertyInfo(value: String, properties: Properties) = original.getPropertyInfo(value, properties)
    override fun getMajorVersion() = original.majorVersion
    override fun getMinorVersion() = original.minorVersion
    override fun jdbcCompliant() = original.jdbcCompliant()
    override fun getParentLogger(): Logger = original.parentLogger
    override fun close() {
        DriverManager.deregisterDriver(this)
        DriverManager.registerDriver(original)
    }
}
