package com.example.ejbapp.soap.client;

import com.example.ejbapp.soap.CalculatorWebService;
import com.example.ejbapp.soap.MemberWebService;
import com.example.ejbapp.soap.dto.MemberListResponse;
import com.example.ejbapp.soap.dto.MemberRequest;
import com.example.ejbapp.soap.dto.MemberResponse;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.math.BigDecimal;
import java.net.URL;

/**
 * SOAP Client Example
 * 
 * This class demonstrates how to consume SOAP web services programmatically.
 * It shows examples for both Member and Calculator web services.
 * 
 * Usage:
 * 1. Ensure the application is deployed and running
 * 2. Run this class as a standalone Java application
 * 3. Check console output for results
 */
public class SoapClientExample {

    // WSDL URLs - update these if your deployment changes
    private static final String MEMBER_WSDL_URL = "http://localhost:8080/demo-ejb2-project/MemberWebService?wsdl";
    private static final String CALCULATOR_WSDL_URL = "http://localhost:8080/demo-ejb2-project/CalculatorWebService?wsdl";
    
    // Target namespace from web service
    private static final String TARGET_NAMESPACE = "http://soap.ejbapp.example.com/";

    public static void main(String[] args) {
        SoapClientExample client = new SoapClientExample();
        
        System.out.println("========================================");
        System.out.println("SOAP Web Services Client Example");
        System.out.println("========================================\n");
        
        // Test Member Web Service
        client.testMemberWebService();
        
        System.out.println("\n========================================\n");
        
        // Test Calculator Web Service
        client.testCalculatorWebService();
        
        System.out.println("\n========================================");
        System.out.println("All tests completed!");
        System.out.println("========================================");
    }

    /**
     * Test Member Web Service operations
     */
    public void testMemberWebService() {
        try {
            System.out.println("Testing Member Web Service...\n");
            
            // Create service and port
            MemberWebService memberService = createMemberWebServicePort();
            
            // Test 1: Register a new member
            System.out.println("1. Registering a new member...");
            MemberRequest request = new MemberRequest();
            request.setName("Jane Smith");
            request.setEmail("jane.smith@example.com");
            request.setPhoneNumber("9876543210");
            
            MemberResponse response = memberService.registerMember(request);
            printMemberResponse(response);
            
            // Store member ID for further operations
            Long memberId = response.getMemberId();
            
            if (memberId != null) {
                // Test 2: Get member by ID
                System.out.println("\n2. Getting member by ID: " + memberId);
                response = memberService.getMemberById(memberId);
                printMemberResponse(response);
                
                // Test 3: Update member
                System.out.println("\n3. Updating member...");
                MemberRequest updateRequest = new MemberRequest();
                updateRequest.setName("Jane Smith Updated");
                updateRequest.setEmail("jane.updated@example.com");
                updateRequest.setPhoneNumber("1111111111");
                
                response = memberService.updateMember(memberId, updateRequest);
                printMemberResponse(response);
                
                // Test 4: Get member by email
                System.out.println("\n4. Getting member by email...");
                response = memberService.getMemberByEmail("jane.updated@example.com");
                printMemberResponse(response);
            }
            
            // Test 5: Get all members
            System.out.println("\n5. Getting all members...");
            MemberListResponse listResponse = memberService.getAllMembers();
            System.out.println("Success: " + listResponse.isSuccess());
            System.out.println("Message: " + listResponse.getMessage());
            System.out.println("Total members: " + listResponse.getMembers().size());
            
            // Clean up: Delete the member we created
            if (memberId != null) {
                System.out.println("\n6. Deleting test member...");
                response = memberService.deleteMember(memberId);
                printMemberResponse(response);
            }
            
        } catch (Exception e) {
            System.err.println("Error testing Member Web Service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test Calculator Web Service operations
     */
    public void testCalculatorWebService() {
        try {
            System.out.println("Testing Calculator Web Service...\n");
            
            // Create service and port
            CalculatorWebService calculatorService = createCalculatorWebServicePort();
            
            // Test 1: Addition
            System.out.println("1. Testing Addition: 100.50 + 25.75");
            BigDecimal result = calculatorService.add(
                new BigDecimal("100.50"),
                new BigDecimal("25.75")
            );
            System.out.println("Result: " + result);
            
            // Test 2: Subtraction
            System.out.println("\n2. Testing Subtraction: 100.50 - 25.75");
            result = calculatorService.subtract(
                new BigDecimal("100.50"),
                new BigDecimal("25.75")
            );
            System.out.println("Result: " + result);
            
            // Test 3: Multiplication
            System.out.println("\n3. Testing Multiplication: 10.5 * 5");
            result = calculatorService.multiply(
                new BigDecimal("10.5"),
                new BigDecimal("5")
            );
            System.out.println("Result: " + result);
            
            // Test 4: Division
            System.out.println("\n4. Testing Division: 100 / 3 (scale: 2)");
            String divisionResult = calculatorService.divide(
                new BigDecimal("100"),
                new BigDecimal("3"),
                2
            );
            System.out.println("Result: " + divisionResult);
            
            // Test 5: Division by zero
            System.out.println("\n5. Testing Division by zero: 100 / 0");
            divisionResult = calculatorService.divide(
                new BigDecimal("100"),
                new BigDecimal("0"),
                2
            );
            System.out.println("Result: " + divisionResult);
            
            // Test 6: Calculate percentage
            System.out.println("\n6. Testing Percentage: 20% of 500");
            result = calculatorService.calculatePercentage(
                new BigDecimal("500"),
                new BigDecimal("20")
            );
            System.out.println("Result: " + result);
            
            // Test 7: Calculate compound interest
            System.out.println("\n7. Testing Compound Interest:");
            System.out.println("   Principal: $1000, Rate: 5%, Compounded: 12 times/year, Years: 5");
            result = calculatorService.calculateCompoundInterest(
                new BigDecimal("1000"),
                new BigDecimal("5"),
                12,
                5
            );
            System.out.println("Result: $" + result);
            
        } catch (Exception e) {
            System.err.println("Error testing Calculator Web Service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create Member Web Service port
     */
    private MemberWebService createMemberWebServicePort() throws Exception {
        URL wsdlURL = new URL(MEMBER_WSDL_URL);
        QName serviceName = new QName(TARGET_NAMESPACE, "MemberWebService");
        Service service = Service.create(wsdlURL, serviceName);
        return service.getPort(MemberWebService.class);
    }

    /**
     * Create Calculator Web Service port
     */
    private CalculatorWebService createCalculatorWebServicePort() throws Exception {
        URL wsdlURL = new URL(CALCULATOR_WSDL_URL);
        QName serviceName = new QName(TARGET_NAMESPACE, "CalculatorWebService");
        Service service = Service.create(wsdlURL, serviceName);
        return service.getPort(CalculatorWebService.class);
    }

    /**
     * Helper method to print member response
     */
    private void printMemberResponse(MemberResponse response) {
        System.out.println("Success: " + response.isSuccess());
        System.out.println("Message: " + response.getMessage());
        if (response.getMemberId() != null) {
            System.out.println("Member ID: " + response.getMemberId());
            System.out.println("Name: " + response.getName());
            System.out.println("Email: " + response.getEmail());
            System.out.println("Phone: " + response.getPhoneNumber());
        }
    }
}
