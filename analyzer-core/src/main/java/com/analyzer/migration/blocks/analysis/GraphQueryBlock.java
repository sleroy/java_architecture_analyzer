package com.analyzer.migration.blocks.analysis;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the H2 graph database to filter nodes by type, tags, or properties.
 * Stores query results in context variables for use by subsequent blocks.
 */
public class GraphQueryBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(GraphQueryBlock.class);

    private final String name;
    private final GraphRepository repository;
    private final QueryType queryType;
    private final String nodeType;
    private final List<String> requiredTags;
    private final String outputVariable;

    private GraphQueryBlock(Builder builder) {
        this.name = builder.name;
        this.repository = builder.repository;
        this.queryType = builder.queryType;
        this.nodeType = builder.nodeType;
        this.requiredTags = builder.requiredTags != null ? new ArrayList<>(builder.requiredTags)
                : new ArrayList<>();
        this.outputVariable = builder.outputVariable;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            List<GraphNode> results;

            switch (queryType) {
                case BY_TYPE:
                    results = queryByType(context);
                    break;
                case BY_TAGS:
                    results = queryByTags(context);
                    break;
                case BY_TYPE_AND_TAGS:
                    results = queryByTypeAndTags(context);
                    break;
                case ALL:
                    results = repository.findAll();
                    break;
                default:
                    return BlockResult.failure("Unknown query type", "Type: " + queryType);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Determine output variable name
            String varName = outputVariable != null ? outputVariable : "query_results";

            // Prepare output variables (will be set in context by TaskExecutor)
            List<String> nodeIds = results.stream()
                    .map(GraphNode::getId)
                    .toList();
            List<String> nodeNames = results.stream()
                    .map(GraphNode::getDisplayLabel)
                    .toList();

            Map<String, Object> summary = new HashMap<>();
            summary.put("count", results.size());
            summary.put("query_type", queryType.toString());
            if (nodeType != null) {
                summary.put("node_type", nodeType);
            }
            if (!requiredTags.isEmpty()) {
                summary.put("tags", requiredTags);
            }

            // Create descriptive success message with count and query details
            String successMessage = buildSuccessMessage(results.size(), executionTime);

            logger.info("Graph query returned {} nodes", results.size());

            // Return all variables via BlockResult - TaskExecutor will add them to context
            return BlockResult.builder()
                    .success(true)
                    .message(successMessage)
                    .outputVariable(varName, results)
                    .outputVariable(varName + "_ids", nodeIds)
                    .outputVariable(varName + "_summary", summary)
                    .outputVariable("node_count", results.size())
                    .outputVariable("result_count", results.size())
                    .outputVariable(varName + "_names", nodeNames)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "Graph query failed",
                    e.getMessage());
        }
    }

    private List<GraphNode> queryByType(MigrationContext context) {
        String processedType = context.substituteVariables(nodeType);
        logger.debug("Querying nodes by type: {}", processedType);
        return repository.findNodesByType(processedType);
    }

    private List<GraphNode> queryByTags(MigrationContext context) {
        List<String> processedTags = requiredTags.stream()
                .map(context::substituteVariables)
                .toList();

        logger.debug("Querying nodes with tags: {} (using optimized database query)", processedTags);

        // Use optimized database query instead of filtering in memory
        if (processedTags.size() == 1) {
            return repository.findNodesByTag(processedTags.getFirst());
        } else {
            // For multiple tags, use OR condition (find nodes with any of these tags)
            return repository.findNodesByAnyTags(processedTags);
        }
    }

    private List<GraphNode> queryByTypeAndTags(MigrationContext context) {
        String processedType = context.substituteVariables(nodeType);
        List<String> processedTags = requiredTags.stream()
                .map(context::substituteVariables)
                .toList();

        logger.debug("Querying nodes by type: {} with tags: {} (using optimized database query)", processedType,
                processedTags);

        // Use optimized database query instead of filtering in memory
        if (processedTags.size() == 1) {
            return repository.findNodesByTypeAndAnyTags(processedType, processedTags);
        } else {
            // For multiple tags, use OR condition (find nodes with any of these tags)
            return repository.findNodesByTypeAndAnyTags(processedType, processedTags);
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.GRAPH_QUERY;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (Graph Query)\n");
        md.append("- Query Type: ").append(queryType).append("\n");

        if (nodeType != null) {
            md.append("- Node Type: `").append(nodeType).append("`\n");
        }
        if (!requiredTags.isEmpty()) {
            md.append("- Required Tags: ").append(String.join(", ", requiredTags)).append("\n");
        }
        md.append("- Output Variable: `").append(outputVariable != null ? outputVariable : "query_results")
                .append("`\n");

        return md.toString();
    }

    @Override
    public boolean validate() {
        if (repository == null) {
            logger.error("Repository is required");
            return false;
        }

        if (queryType == null) {
            logger.error("Query type is required");
            return false;
        }

        switch (queryType) {
            case BY_TYPE, BY_TYPE_AND_TAGS:
                if (nodeType == null || nodeType.isEmpty()) {
                    logger.error("Node type is required for {} query", queryType);
                    return false;
                }
                break;
            case BY_TAGS:
                if (requiredTags.isEmpty()) {
                    logger.error("Tags are required for BY_TAGS query");
                    return false;
                }
                break;
            case ALL:
                break;
        }

        if (queryType == QueryType.BY_TYPE_AND_TAGS) {
            if (requiredTags.isEmpty()) {
                logger.error("Tags are required for BY_TYPE_AND_TAGS query");
                return false;
            }
        }

        return true;
    }

    /**
     * Builds a descriptive success message including result count and query
     * details.
     */
    private String buildSuccessMessage(int resultCount, long executionTimeMs) {
        StringBuilder message = new StringBuilder();

        message.append("Found ").append(resultCount).append(" node");
        if (resultCount != 1) {
            message.append("s");
        }

        // Add query criteria details
        switch (queryType) {
            case BY_TYPE:
                message.append(" of type '").append(nodeType).append("'");
                break;
            case BY_TAGS:
                message.append(" with tag").append(requiredTags.size() > 1 ? "s" : "")
                        .append(" [").append(String.join(", ", requiredTags)).append("]");
                break;
            case BY_TYPE_AND_TAGS:
                message.append(" of type '").append(nodeType).append("' with tag")
                        .append(requiredTags.size() > 1 ? "s" : "")
                        .append(" [").append(String.join(", ", requiredTags)).append("]");
                break;
            case ALL:
                message.append(" (all nodes)");
                break;
        }

        // Add timing information
        message.append(" (").append(executionTimeMs).append("ms)");

        return message.toString();
    }

    public enum QueryType {
        /**
         * Query nodes by type only
         */
        BY_TYPE,

        /**
         * Query nodes by tags only
         */
        BY_TAGS,

        /**
         * Query nodes by both type and tags
         */
        BY_TYPE_AND_TAGS,

        /**
         * Return all nodes
         */
        ALL
    }

    public static class Builder {
        private String name;
        private GraphRepository repository;
        private QueryType queryType;
        private String nodeType;
        private List<String> requiredTags;
        private String outputVariable;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder repository(GraphRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder queryType(QueryType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder requiredTags(List<String> requiredTags) {
            this.requiredTags = requiredTags;
            return this;
        }

        public Builder requiredTag(String tag) {
            if (this.requiredTags == null) {
                this.requiredTags = new ArrayList<>();
            }
            this.requiredTags.add(tag);
            return this;
        }

        public Builder outputVariable(String outputVariable) {
            this.outputVariable = outputVariable;
            return this;
        }

        public GraphQueryBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (repository == null) {
                throw new IllegalStateException("Repository is required");
            }
            if (queryType == null) {
                throw new IllegalStateException("Query type is required");
            }
            return new GraphQueryBlock(this);
        }
    }
}
