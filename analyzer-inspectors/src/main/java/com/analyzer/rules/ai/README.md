# AI-Powered Inspectors

This package contains inspectors that use AWS Bedrock AI models for advanced code analysis.

## BusinessVsFrameworkClassifierInspector

### Overview

The `BusinessVsFrameworkClassifierInspector` uses Large Language Models (LLMs) via AWS Bedrock to classify Java code as either:

- **BUSINESS code**: Domain-specific logic, business rules, and use cases
- **FRAMEWORK code**: Generic infrastructure, utilities, and reusable technical components

This classification helps identify which parts of a codebase contain business value vs. reusable technical infrastructure.

### Output

The inspector produces:

1. **Classification Tag**: `BUSINESS` or `FWK`
2. **Metrics**:
   - `businessScore` (0.0-1.0): Likelihood that the code is business-specific
   - `frameworkScore` (0.0-1.0): Likelihood that the code is framework/infrastructure
3. **Property** (optional):
   - `classificationReasoning`: Brief explanation of the classification

### Classification Criteria

#### BUSINESS CODE Indicators

- Domain-specific class names (Customer, Order, Invoice, Payment, Account)
- Business rules and validation logic
- Industry-specific calculations
- Use cases and workflows
- Entity/model classes with business meaning
- Service classes implementing business processes

**Examples:**
- `CustomerService.java` - manages customer lifecycle
- `OrderProcessor.java` - handles order fulfillment
- `InvoiceCalculator.java` - calculates invoice totals
- `PaymentValidator.java` - validates payment rules

#### FRAMEWORK CODE Indicators

- Generic utility classes (StringUtils, DateUtils, CollectionUtils)
- Connection pooling, caching, logging infrastructure
- Base/abstract classes for reuse
- Configuration management
- DAO/Repository patterns (generic data access)
- Transaction management
- Generic helpers and wrappers

**Examples:**
- `ConnectionManager.java` - manages database connections
- `JdbcHelper.java` - JDBC utility methods
- `AbstractEntityManager.java` - generic CRUD base class
- `TransactionManager.java` - transaction handling

### Configuration

The inspector requires AWS Bedrock configuration. See the main project documentation for Bedrock setup.

**Bedrock Configuration** (`bedrock.properties` or environment variables):

```properties
bedrock.enabled=true
bedrock.modelId=us.anthropic.claude-3-5-sonnet-20241022-v2:0
bedrock.region=us-east-1
bedrock.maxTokens=2048
bedrock.temperature=0.0
```

### Usage in Analysis Plans

Add the inspector to your YAML analysis plan:

```yaml
inspectors:
  - name: BusinessVsFrameworkClassifierInspector
    enabled: true
```

### Example Output

For `Customer.java` (a domain entity):

```
Tag: BUSINESS
Metrics:
  businessScore: 0.95
  frameworkScore: 0.05
Property:
  classificationReasoning: "This class represents a core business entity (Customer) 
  with domain-specific fields and business logic for customer management."
```

For `ConnectionManager.java` (infrastructure):

```
Tag: FWK
Metrics:
  businessScore: 0.10
  frameworkScore: 0.90
Property:
  classificationReasoning: "This is a generic infrastructure class providing database 
  connection pooling. It's a reusable technical component with no business domain knowledge."
```

### Querying Results

Use GraphQL queries to filter by classification:

```graphql
# Find all business code
{
  nodes(tags: ["BUSINESS"]) {
    fullyQualifiedName
    businessScore
    classificationReasoning
  }
}

# Find all framework code
{
  nodes(tags: ["FWK"]) {
    fullyQualifiedName
    frameworkScore
    classificationReasoning
  }
}

# Find highly confident business code
{
  nodes(tags: ["BUSINESS"], metricFilter: {businessScore: {gt: 0.8}}) {
    fullyQualifiedName
    businessScore
  }
}
```

### Use Cases

1. **Migration Planning**: Identify business logic that needs migration vs. framework code that can be reused or replaced
2. **Code Ownership**: Assign business code to domain teams and framework code to platform teams
3. **Refactoring Priorities**: Focus on extracting reusable framework code from business logic
4. **Documentation**: Generate separate documentation for business logic vs. technical infrastructure
5. **Testing Strategy**: Apply different testing strategies (business logic vs. infrastructure testing)

### Performance Considerations

- **API Calls**: Each class requires one Bedrock API call
- **Cost**: AWS Bedrock charges per token (input + output)
- **Time**: Processing time depends on model and class size
- **Caching**: Results are cached per analysis run

**Recommendations:**
- Use on targeted subsets of code rather than entire codebase
- Consider running as a separate analysis phase
- Monitor AWS costs if analyzing large codebases

### Integration with Demo JDBC Project

The demo EJB2 project (`demo-ejb2-project/src/main/java/com/example/ejbapp/jdbc/`) provides perfect examples:

**Expected BUSINESS classifications:**
- `Customer.java` - Domain entity
- `CustomerManager.java` - Business service (though it uses generic patterns)
- `CustomerRestService.java` - Business API endpoint
- `CustomerSoapService.java` - Business web service

**Expected FWK classifications:**
- `ConnectionManager.java` - Infrastructure singleton
- `JdbcHelper.java` - Generic JDBC utilities
- `TransactionManager.java` - Generic transaction management
- `AbstractEntityManager.java` - Generic base class
- `CustomerDAO.java` - Generic data access pattern

### Troubleshooting

**Inspector not running:**
- Check that `bedrock.enabled=true` in configuration
- Verify AWS credentials are configured
- Check logs for configuration errors

**Poor classification results:**
- The model may struggle with ambiguous code
- Consider the context: a "Customer" class in a framework package might be a test fixture
- Review the `classificationReasoning` property for insights

**API Errors:**
- Check AWS credentials and permissions
- Verify the model ID is correct and available in your region
- Check AWS service quotas

### Advanced Configuration

**Custom Prompts:**
Extend `BusinessVsFrameworkClassifierInspector` and override `buildPrompt()` to customize the AI prompt.

**Custom Parsing:**
Override `parseResponse()` to handle different response formats or extract additional information.

**Different Models:**
Configure different Bedrock models via `bedrock.modelId`:
- `anthropic.claude-3-sonnet` - Good balance of speed and quality
- `anthropic.claude-3-opus` - Highest quality, slower
- `amazon.titan-text` - Lower cost alternative

### Limitations

1. **Subjectivity**: Classification can be subjective, especially for mixed-purpose classes
2. **Context-Dependent**: A class name like "Helper" could be business or framework depending on context
3. **AI Limitations**: LLMs can make mistakes, especially with unusual code patterns
4. **Cost**: Running on large codebases can incur significant AWS costs

### Future Enhancements

Potential improvements:
- Multi-class classification (business, framework, test, config, etc.)
- Confidence scores for uncertain classifications
- Batch processing to reduce API calls
- Integration with package structure analysis
- Historical trend analysis

## See Also

- [AWS Bedrock Documentation](https://docs.aws.amazon.com/bedrock/)
- [Main Bedrock Integration Guide](../../../../../docs/bedrock-integration.md)
- [Demo EJB2 Project](../../../../../../demo-ejb2-project/src/main/java/com/example/ejbapp/jdbc/README.md)
