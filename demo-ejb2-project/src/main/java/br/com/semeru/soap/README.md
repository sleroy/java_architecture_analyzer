# SOAP Web Services Documentation

This directory contains SOAP (Simple Object Access Protocol) web service controllers for the Semeru EJB Maven application.

## Overview

The SOAP web services are implemented using JAX-WS (Java API for XML Web Services) annotations and follow the contract-first approach with separate interface and implementation classes.

## Available Web Services

### 1. MemberWebService
Full CRUD operations for member management.

**WSDL Location**: `http://localhost:8080/semeru-ejb-maven-web/MemberWebService?wsdl`

**Operations**:
- `registerMember(MemberRequest)` - Register a new member
- `getMemberById(Long)` - Retrieve member by ID
- `getMemberByEmail(String)` - Retrieve member by email
- `getAllMembers()` - Retrieve all members
- `updateMember(Long, MemberRequest)` - Update member information
- `deleteMember(Long)` - Delete a member

**Example Request (registerMember)**:
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
                  xmlns:soap="http://soap.semeru.com.br/">
   <soapenv:Header/>
   <soapenv:Body>
      <soap:registerMember>
         <memberRequest>
            <name>John Doe</name>
            <email>john.doe@example.com</email>
            <phoneNumber>1234567890</phoneNumber>
         </memberRequest>
      </soap:registerMember>
   </soapenv:Body>
</soapenv:Envelope>
```

**Example Response**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
   <soap:Body>
      <ns2:registerMemberResponse xmlns:ns2="http://soap.semeru.com.br/">
         <memberResponse>
            <success>true</success>
            <message>Member registered successfully</message>
            <memberId>1</memberId>
            <name>John Doe</name>
            <email>john.doe@example.com</email>
            <phoneNumber>1234567890</phoneNumber>
         </memberResponse>
      </ns2:registerMemberResponse>
   </soap:Body>
</soap:Envelope>
```

### 2. CalculatorWebService
Mathematical calculation operations.

**WSDL Location**: `http://localhost:8080/semeru-ejb-maven-web/CalculatorWebService?wsdl`

**Operations**:
- `add(BigDecimal, BigDecimal)` - Add two numbers
- `subtract(BigDecimal, BigDecimal)` - Subtract two numbers
- `multiply(BigDecimal, BigDecimal)` - Multiply two numbers
- `divide(BigDecimal, BigDecimal, int)` - Divide two numbers with scale
- `calculatePercentage(BigDecimal, BigDecimal)` - Calculate percentage
- `calculateCompoundInterest(BigDecimal, BigDecimal, int, int)` - Calculate compound interest

**Example Request (add)**:
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
                  xmlns:soap="http://soap.semeru.com.br/">
   <soapenv:Header/>
   <soapenv:Body>
      <soap:add>
         <a>100.50</a>
         <b>25.75</b>
      </soap:add>
   </soapenv:Body>
</soapenv:Envelope>
```

**Example Response**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
   <soap:Body>
      <ns2:addResponse xmlns:ns2="http://soap.semeru.com.br/">
         <result>126.25</result>
      </ns2:addResponse>
   </soap:Body>
</soap:Envelope>
```

## Architecture

### Interface-Implementation Pattern
Each web service follows the contract-first approach:
- **Interface** (`*WebService.java`): Defines the service contract with JAX-WS annotations
- **Implementation** (`*WebServiceImpl.java`): Implements the business logic

### Key Components

#### Interfaces
- `MemberWebService.java` - Member operations contract
- `CalculatorWebService.java` - Calculator operations contract

#### Implementations
- `MemberWebServiceImpl.java` - Member operations implementation
- `CalculatorWebServiceImpl.java` - Calculator operations implementation

#### DTOs (Data Transfer Objects)
Located in `dto/` package:
- `MemberRequest.java` - Request DTO for member operations
- `MemberResponse.java` - Response DTO for single member
- `MemberListResponse.java` - Response DTO for member lists

## JAX-WS Annotations

### @WebService
Marks a class or interface as a web service endpoint.

**Interface**:
```java
@WebService(name = "MemberWebService", targetNamespace = "http://soap.semeru.com.br/")
```

**Implementation**:
```java
@WebService(
    serviceName = "MemberWebService",
    portName = "MemberWebServicePort",
    endpointInterface = "br.com.semeru.soap.MemberWebService",
    targetNamespace = "http://soap.semeru.com.br/"
)
```

### @WebMethod
Defines an operation that is exposed as a web service operation.

```java
@WebMethod(operationName = "registerMember")
```

### @WebParam
Specifies the mapping of method parameters to WSDL parts and XML elements.

```java
@WebParam(name = "memberRequest") MemberRequest request
```

### @WebResult
Specifies the mapping of the return value to a WSDL part and XML element.

```java
@WebResult(name = "memberResponse")
```

### @SOAPBinding
Specifies the SOAP binding style for the web service.

```java
@SOAPBinding(
    style = SOAPBinding.Style.DOCUMENT,
    use = SOAPBinding.Use.LITERAL,
    parameterStyle = SOAPBinding.ParameterStyle.WRAPPED
)
```

### XML Binding Annotations
For DTOs:
- `@XmlRootElement` - Defines the root element
- `@XmlAccessorType` - Specifies how fields are accessed
- `@XmlType` - Defines type mapping and property order
- `@XmlElement` - Maps a field to an XML element

## Testing SOAP Services

### Using SoapUI

1. **Download and install SoapUI**: https://www.soapui.org/
2. **Create a new SOAP project**
3. **Import WSDL**: Enter the WSDL URL
4. **Execute requests**: Use the generated request templates

### Using cURL

```bash
# Register a new member
curl -X POST http://localhost:8080/semeru-ejb-maven-web/MemberWebService \
  -H "Content-Type: text/xml" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://soap.semeru.com.br/">
   <soapenv:Header/>
   <soapenv:Body>
      <soap:registerMember>
         <memberRequest>
            <name>John Doe</name>
            <email>john.doe@example.com</email>
            <phoneNumber>1234567890</phoneNumber>
         </memberRequest>
      </soap:registerMember>
   </soapenv:Body>
</soapenv:Envelope>'
```

### Using Java Client

```java
// Create service
URL wsdlURL = new URL("http://localhost:8080/semeru-ejb-maven-web/MemberWebService?wsdl");
QName serviceName = new QName("http://soap.semeru.com.br/", "MemberWebService");
Service service = Service.create(wsdlURL, serviceName);

// Get port
MemberWebService port = service.getPort(MemberWebService.class);

// Create request
MemberRequest request = new MemberRequest();
request.setName("John Doe");
request.setEmail("john.doe@example.com");
request.setPhoneNumber("1234567890");

// Call service
MemberResponse response = port.registerMember(request);
System.out.println("Success: " + response.isSuccess());
System.out.println("Message: " + response.getMessage());
```

## Deployment

### JBoss/WildFly Configuration

The web services are automatically deployed with the application. No additional configuration is required for basic deployment.

### web.xml Configuration (if needed)

If you need to customize the SOAP endpoint URL, add this to `WEB-INF/web.xml`:

```xml
<servlet>
    <servlet-name>MemberWebService</servlet-name>
    <servlet-class>br.com.semeru.soap.MemberWebServiceImpl</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>MemberWebService</servlet-name>
    <url-pattern>/services/MemberWebService</url-pattern>
</servlet-mapping>
```

### Accessing WSDL

After deployment, access the WSDL at:
- Member Service: `http://localhost:8080/semeru-ejb-maven-web/MemberWebService?wsdl`
- Calculator Service: `http://localhost:8080/semeru-ejb-maven-web/CalculatorWebService?wsdl`

## Error Handling

All web services implement comprehensive error handling:

1. **Input Validation**: Validates all input parameters
2. **Business Logic Errors**: Catches and logs service layer exceptions
3. **Graceful Responses**: Returns meaningful error messages in responses
4. **Logging**: All errors are logged with appropriate severity levels

Example error response:
```xml
<memberResponse>
    <success>false</success>
    <message>Member email is required</message>
</memberResponse>
```

## Best Practices

1. **Separation of Concerns**: Keep interface and implementation separate
2. **DTO Usage**: Use DTOs for request/response to avoid exposing entity classes
3. **Validation**: Always validate input parameters
4. **Error Handling**: Provide meaningful error messages
5. **Logging**: Log all operations for debugging and monitoring
6. **Documentation**: Keep WSDL and documentation in sync
7. **Versioning**: Consider versioning strategy for breaking changes

## Security Considerations

For production deployments, consider:

1. **WS-Security**: Implement authentication and encryption
2. **SSL/TLS**: Use HTTPS for all SOAP communications
3. **Input Validation**: Validate and sanitize all inputs
4. **Rate Limiting**: Implement rate limiting to prevent abuse
5. **Logging**: Log security events for auditing

## Performance Optimization

1. **Connection Pooling**: Use database connection pooling
2. **Caching**: Cache frequently accessed data
3. **Batch Operations**: Support batch operations where appropriate
4. **Async Processing**: Consider async operations for long-running tasks
5. **Monitoring**: Monitor response times and resource usage

## Troubleshooting

### Common Issues

1. **WSDL Not Accessible**
   - Verify application is deployed
   - Check server logs for errors
   - Verify URL and port

2. **Method Not Found**
   - Verify method name in WSDL
   - Check operation name in @WebMethod annotation

3. **Serialization Errors**
   - Ensure DTOs have proper JAXB annotations
   - Verify all fields have getters/setters

4. **Dependency Injection Failures**
   - Check beans.xml exists
   - Verify @Inject annotations
   - Check service availability

## Additional Resources

- [JAX-WS Specification](https://javaee.github.io/metro-jax-ws/)
- [JAXB Documentation](https://javaee.github.io/jaxb-v2/)
- [SOAP Tutorial](https://www.w3schools.com/xml/xml_soap.asp)
- [WildFly Web Services Documentation](https://docs.wildfly.org/26/Developer_Guide.html#Web_Services)

## Version History

- **v1.0** - Initial implementation with Member and Calculator services
  - Full CRUD operations for members
  - Basic and advanced calculator operations
  - Comprehensive error handling and validation
