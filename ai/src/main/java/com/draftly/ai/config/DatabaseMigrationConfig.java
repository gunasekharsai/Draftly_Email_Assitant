package com.draftly.ai.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseMigrationConfig {
    @Bean
    CommandLineRunner widenTextColumns(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS email_message ALTER COLUMN body TYPE TEXT");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS email_message ALTER COLUMN classification_reason TYPE TEXT");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS reply_draft ALTER COLUMN draft_content TYPE TEXT");
        };
    }
}
