package com.laundry.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe custom JDBC connection pool backed by an ArrayBlockingQueue.
 * Validates connections on borrow, replaces stale connections, returns a
 * Connection proxy so try-with-resources close() returns the connection to
 * the pool, and registers a JVM shutdown hook to close all connections.
 */
public class ConnectionPool {

    private static final Logger LOGGER = Logger.getLogger(ConnectionPool.class.getName());
    private static final int VALIDATION_TIMEOUT_SEC = 2;
    private static final long BORROW_TIMEOUT_MS = 5000L;

    private final ArrayBlockingQueue<Connection> pool;
    private final String url;
    private final String user;
    private final String password;
    private final int poolSize;
    private volatile boolean closed = false;

    public ConnectionPool() throws SQLException {
        this(
                DatabaseConfig.getDbUrl(),
                DatabaseConfig.getDbUser(),
                DatabaseConfig.getDbPass(),
                DatabaseConfig.getPoolSize()
        );
    }

    public ConnectionPool(String url, String user, String password, int poolSize) throws SQLException {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("Pool size must be positive");
        }
        this.url = url;
        this.user = user;
        this.password = password;
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL Connector/J driver not found on classpath", e);
        }

        for (int i = 0; i < poolSize; i++) {
            pool.add(createConnection());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::closeAll, "db-pool-shutdown"));
        LOGGER.info("ConnectionPool initialized with " + poolSize + " connections");
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Borrows a validated connection wrapped in a proxy so calling close()
     * (including via try-with-resources) restores autoCommit=true and returns
     * the underlying physical connection to the pool.
     */
    public Connection borrowConnection() throws SQLException {
        if (closed) {
            throw new SQLException("ConnectionPool is closed");
        }
        Connection raw;
        try {
            raw = pool.poll(BORROW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a database connection", e);
        }
        if (raw == null) {
            throw new SQLException("Timed out waiting for a database connection from pool");
        }

        try {
            if (raw.isClosed() || !raw.isValid(VALIDATION_TIMEOUT_SEC)) {
                closeQuietly(raw);
                raw = createConnection();
            }
            if (!raw.getAutoCommit()) {
                raw.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            closeQuietly(raw);
            raw = createConnection();
        }

        return wrapConnection(raw);
    }

    private Connection wrapConnection(Connection raw) {
        final boolean[] returned = {false};
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (!returned[0]) {
                            returned[0] = true;
                            releaseConnection(raw);
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return returned[0] || raw.isClosed();
                    }
                    if ("unwrap".equals(name) && args != null && args.length == 1) {
                        Class<?> iface = (Class<?>) args[0];
                        if (iface.isInstance(raw)) {
                            return raw;
                        }
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
    }

    /**
     * Returns a physical connection back to the pool, restoring autoCommit=true.
     */
    public void releaseConnection(Connection raw) {
        if (raw == null) {
            return;
        }
        if (closed) {
            closeQuietly(raw);
            return;
        }
        try {
            if (raw.isClosed()) {
                offerReplacement();
                return;
            }
            if (!raw.getAutoCommit()) {
                raw.setAutoCommit(true);
            }
            if (!pool.offer(raw)) {
                closeQuietly(raw);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to reset connection before returning to pool", e);
            closeQuietly(raw);
            offerReplacement();
        }
    }

    private void offerReplacement() {
        if (closed) {
            return;
        }
        try {
            Connection fresh = createConnection();
            if (!pool.offer(fresh)) {
                closeQuietly(fresh);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Could not create replacement connection", e);
        }
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getAvailableCount() {
        return pool.size();
    }

    public synchronized void closeAll() {
        if (closed) {
            return;
        }
        closed = true;
        Connection conn;
        while ((conn = pool.poll()) != null) {
            closeQuietly(conn);
        }
        LOGGER.info("All pooled database connections closed");
    }

    private void closeQuietly(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Ignoring exception while closing connection", e);
        }
    }
}
