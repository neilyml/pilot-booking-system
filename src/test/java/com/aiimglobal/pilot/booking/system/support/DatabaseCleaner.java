package com.aiimglobal.pilot.booking.system.support;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

public final class DatabaseCleaner {

    private static final Set<String> PRESERVED_TABLES = Set.of("flyway_schema_history", "roles");

    private final JdbcTemplate jdbcTemplate;

    DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        List<String> applicationTables = jdbcTemplate.queryForList("""
                        select table_name
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_type = 'BASE TABLE'
                        order by table_name
                        """, String.class).stream()
                .filter(table -> !PRESERVED_TABLES.contains(table))
                .toList();

        if (applicationTables.isEmpty()) {
            return;
        }

        String tables = applicationTables.stream()
                .map(DatabaseCleaner::quoteIdentifier)
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("truncate table " + tables + " restart identity cascade");
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
