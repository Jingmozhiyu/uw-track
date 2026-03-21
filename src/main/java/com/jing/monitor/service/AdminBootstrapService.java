package com.jing.monitor.service;

import com.jing.monitor.model.User;
import com.jing.monitor.repository.TaskRepository;
import com.jing.monitor.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Startup bootstrap service for authentication-related data migration.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Ensure the configured admin account exists.</li>
 *   <li>Backfill legacy tasks with missing owners.</li>
 *   <li>Migrate tasks from legacy default user to admin user.</li>
 *   <li>Enforce composite uniqueness on task ownership and section id.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService {

    @Value("${app.auth.admin.email:admin@uw-course-monitor.local}")
    private String adminEmail;

    @Value("${app.auth.admin.password:admin123456}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Runs at startup to prepare auth baseline data and schema expectations.
     */
    @PostConstruct
    public void initAdminUser() {
        ensureTaskOwnershipUniqueConstraint();

        User admin = userRepository.findByEmail(adminEmail)
                .orElseGet(() -> {
                    User user = new User(adminEmail, passwordEncoder.encode(adminPassword));
                    User saved = userRepository.save(user);
                    log.info("[Auth] Created admin user {}", adminEmail);
                    return saved;
                });

        int nullUpdated = taskRepository.assignUserIdToNullTasks(admin.getId());
        if (nullUpdated > 0) {
            log.info("[Auth] Assigned {} existing tasks to admin user {}", nullUpdated, adminEmail);
        }
    }

    /**
     * Ensures the task table uses ownership-based uniqueness.
     * <p>
     * It removes legacy unique indexes on {@code section_id} only, then creates
     * the composite unique index {@code (user_id, section_id)} if missing.
     */
    private void ensureTaskOwnershipUniqueConstraint() {
        try {
            List<String> legacyIndexes = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND NON_UNIQUE = 0 " +
                            "GROUP BY INDEX_NAME " +
                            "HAVING SUM(CASE WHEN COLUMN_NAME = 'section_id' THEN 1 ELSE 0 END) = 1 " +
                            "AND SUM(CASE WHEN COLUMN_NAME = 'user_id' THEN 1 ELSE 0 END) = 0 " +
                            "AND COUNT(*) = 1 " +
                            "AND INDEX_NAME <> 'PRIMARY'",
                    String.class
            );

            for (String indexName : legacyIndexes) {
                String safeIndexName = indexName.replace("`", "``");
                jdbcTemplate.execute("ALTER TABLE tasks DROP INDEX `" + safeIndexName + "`");
                log.info("[Auth] Dropped legacy unique index on tasks.section_id: {}", indexName);
            }

            Integer compositeIndexCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND INDEX_NAME = 'uk_tasks_user_section'",
                    Integer.class
            );

            if (compositeIndexCount == null || compositeIndexCount == 0) {
                jdbcTemplate.execute("ALTER TABLE tasks ADD UNIQUE INDEX uk_tasks_user_section (user_id, section_id)");
                log.info("[Auth] Added unique index uk_tasks_user_section(user_id, section_id)");
            }
        } catch (Exception e) {
            log.warn("[Auth] Failed to enforce task unique constraint; please check DB schema manually", e);
        }
    }
}
