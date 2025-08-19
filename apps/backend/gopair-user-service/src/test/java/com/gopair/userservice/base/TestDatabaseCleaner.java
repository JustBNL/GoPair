package com.gopair.userservice.base;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TestDatabaseCleaner {

	private final JdbcTemplate jdbcTemplate;

	private final Set<String> ignoredTables = new HashSet<>();

	public TestDatabaseCleaner(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		// 如有需要，可将需要忽略的表加入（例如迁移历史表）
		// ignoredTables.add("flyway_schema_history");
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void cleanDatabase() {
		try {
			jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

			List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = database() AND table_type='BASE TABLE'",
				String.class
			);

			for (String table : tables) {
				if (ignoredTables.contains(table)) {
					continue;
				}
				jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
			}
		} finally {
			jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
		}
	}
} 