package com.hms.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Reads connection settings and hands out JDBC connections.
 *
 * Settings come from environment variables first (HMS_DB_URL, HMS_DB_USER,
 * HMS_DB_PASSWORD) and fall back to db.properties in the working directory.
 */
public final class Database implements ConnectionProvider {

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database fromEnvironment() throws IOException {
        Properties props = new Properties();
        Path file = Path.of("db.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        }
        String url = firstNonBlank(System.getenv("HMS_DB_URL"), props.getProperty("db.url"),
                "jdbc:mysql://localhost:3306/hms?allowPublicKeyRetrieval=true&useSSL=false");
        String user = firstNonBlank(System.getenv("HMS_DB_USER"), props.getProperty("db.user"), "root");
        String pass = firstNonBlank(System.getenv("HMS_DB_PASSWORD"), props.getProperty("db.password"), "");
        return new Database(url, user, pass);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public String url() {
        return url;
    }

    /** Runs a .sql file statement by statement. Good enough for schema.sql/seed.sql. */
    public void runScript(Path sqlFile) throws IOException, SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            for (String sql : splitStatements(Files.readString(sqlFile))) {
                st.execute(sql);
            }
        }
    }

    static List<String> splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.trim());
            }
        }
        return statements;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
