package com.laundry.config;

/**
 * Central database and server configuration.
 * Values can be overridden via environment variables:
 * DB_URL, DB_USER, DB_PASS, DB_POOL_SIZE, PORT, SERVER_PORT, FRONTEND_DIR.
 */
public final class DatabaseConfig {

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/laundry_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASS = "";
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_FRONTEND_DIR = "frontend";

    private DatabaseConfig() {
    }

    public static String getDbUrl() {
        return getEnvOrDefault("DB_URL", DEFAULT_DB_URL);
    }

    public static String getDbUser() {
        return getEnvOrDefault("DB_USER", DEFAULT_DB_USER);
    }

    public static String getDbPass() {
        String val = System.getenv("DB_PASS");
        return val != null ? val : DEFAULT_DB_PASS;
    }

    public static int getPoolSize() {
        String raw = System.getenv("DB_POOL_SIZE");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_POOL_SIZE;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : DEFAULT_POOL_SIZE;
        } catch (NumberFormatException e) {
            return DEFAULT_POOL_SIZE;
        }
    }

    public static int getServerPort() {
        String raw = System.getenv("PORT");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("SERVER_PORT");
        }
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SERVER_PORT;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : DEFAULT_SERVER_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_SERVER_PORT;
        }
    }
    public static String getFrontendDir() {
        return getEnvOrDefault("FRONTEND_DIR", DEFAULT_FRONTEND_DIR);
    }

    private static String getEnvOrDefault(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val.trim() : fallback;
    }
}
