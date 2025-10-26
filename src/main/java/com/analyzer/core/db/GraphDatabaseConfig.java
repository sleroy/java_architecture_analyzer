package com.analyzer.core.db;

import com.analyzer.core.db.mapper.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Configuration class for the H2 graph database with MyBatis.
 * Handles database initialization, schema creation, and SqlSessionFactory setup.
 */
public class GraphDatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(GraphDatabaseConfig.class);

    private SqlSessionFactory sqlSessionFactory;
    private String jdbcUrl;
    private boolean initialized = false;

    /**
     * Initialize the database at the specified path.
     *
     * @param databasePath Path to the H2 database file (without extension)
     * @throws Exception if initialization fails
     */
    public void initialize(Path databasePath) throws Exception {
        if (initialized) {
            logger.warn("Database already initialized");
            return;
        }

        logger.info("Initializing H2 database at: {}", databasePath);

        // Ensure parent directory exists
        if (databasePath.getParent() != null) {
            Files.createDirectories(databasePath.getParent());
        }

        // Build JDBC URL for H2
        this.jdbcUrl = "jdbc:h2:" + databasePath.toString() + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

        // Initialize schema
        initializeSchema();

        // Build MyBatis SqlSessionFactory
        buildSqlSessionFactory();

        initialized = true;
        logger.info("Database initialized successfully");
    }

    /**
     * Initialize database schema by executing schema.sql
     */
    private void initializeSchema() throws Exception {
        logger.info("Initializing database schema...");

        // Read schema.sql from resources
        String schemaSQL;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (is == null) {
                throw new IOException("schema.sql not found in classpath");
            }
            schemaSQL = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        // Execute using H2's RunScript utility
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            // Use H2's RunScript class to execute the complete script
            org.h2.tools.RunScript.execute(conn, new java.io.StringReader(schemaSQL));
            logger.info("Database schema initialized successfully");
        }
    }

    /**
     * Build MyBatis SqlSessionFactory programmatically
     */
    private void buildSqlSessionFactory() throws Exception {
        logger.info("Building MyBatis SqlSessionFactory...");

        // Create pooled datasource for H2
        PooledDataSource dataSource = new PooledDataSource();
        dataSource.setDriver("org.h2.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        // Create transaction factory
        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();

        // Create environment
        Environment environment = new Environment("development", transactionFactory, dataSource);

        // Build configuration
        Configuration configuration = new Configuration(environment);

        // Configure settings from mybatis-config.xml
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLazyLoadingEnabled(true);
        configuration.setAggressiveLazyLoading(false);
        configuration.setCacheEnabled(true);
        configuration.setUseActualParamName(true);
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);

        // Register type aliases
        configuration.getTypeAliasRegistry().registerAlias("GraphNode", com.analyzer.core.db.entity.GraphNodeEntity.class);
        configuration.getTypeAliasRegistry().registerAlias("GraphEdge", com.analyzer.core.db.entity.GraphEdgeEntity.class);
        configuration.getTypeAliasRegistry().registerAlias("NodeTag", com.analyzer.core.db.entity.NodeTagEntity.class);
        configuration.getTypeAliasRegistry().registerAlias("Project", com.analyzer.core.db.entity.ProjectEntity.class);

        // Load mapper XML files using XMLMapperBuilder
        String[] mapperResources = {
            "mybatis/mappers/NodeMapper.xml",
            "mybatis/mappers/EdgeMapper.xml",
            "mybatis/mappers/TagMapper.xml",
            "mybatis/mappers/ProjectMapper.xml"
        };

        for (String mapperResource : mapperResources) {
            try (InputStream is = Resources.getResourceAsStream(mapperResource)) {
                XMLMapperBuilder mapperParser = new XMLMapperBuilder(is, configuration, mapperResource, configuration.getSqlFragments());
                mapperParser.parse();
            }
        }

        // Build SqlSessionFactory
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        logger.info("SqlSessionFactory built successfully");
    }

    /**
     * Get a new SqlSession for database operations.
     * Remember to close the session after use.
     *
     * @return New SqlSession instance
     */
    public SqlSession openSession() {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return sqlSessionFactory.openSession();
    }

    /**
     * Get a new SqlSession with auto-commit enabled.
     *
     * @param autoCommit Whether to enable auto-commit
     * @return New SqlSession instance
     */
    public SqlSession openSession(boolean autoCommit) {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return sqlSessionFactory.openSession(autoCommit);
    }

    /**
     * Get the SqlSessionFactory for advanced usage.
     *
     * @return SqlSessionFactory instance
     */
    public SqlSessionFactory getSqlSessionFactory() {
        if (!initialized) {
            throw new IllegalStateException("Database not initialized. Call initialize() first.");
        }
        return sqlSessionFactory;
    }

    /**
     * Get the JDBC URL for direct access.
     *
     * @return JDBC URL string
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Check if database is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Close all database connections and resources.
     */
    public void close() {
        if (initialized) {
            logger.info("Closing database connections...");
            // MyBatis handles connection pooling internally
            initialized = false;
            logger.info("Database connections closed");
        }
    }
}
