package com.example.ejbapp.soap;

import com.example.ejbapp.service.CalculatorService;

import javax.inject.Inject;
import javax.jws.WebService;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SOAP Web Service Implementation for Calculator operations
 * 
 * This implementation provides SOAP endpoints for mathematical calculations
 * including basic operations and financial calculations.
 * 
 * WSDL available at: http://localhost:8080/demo-ejb2-project/CalculatorWebService?wsdl
 */
@WebService(
    serviceName = "CalculatorWebService",
    portName = "CalculatorWebServicePort",
    endpointInterface = "com.example.ejbapp.soap.CalculatorWebService",
    targetNamespace = "http://soap.example.com/"
)
public class CalculatorWebServiceImpl implements CalculatorWebService {

    @Inject
    private Logger log;

    @Inject
    private CalculatorService calculatorService;

    @Override
    public BigDecimal add(BigDecimal a, BigDecimal b) {
        log.info("SOAP: Adding " + a + " + " + b);
        
        try {
            if (a == null || b == null) {
                log.warning("Null parameters received for addition");
                return BigDecimal.ZERO;
            }
            
            return calculatorService.add(a, b);
            
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error performing addition via SOAP", e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        log.info("SOAP: Subtracting " + a + " - " + b);
        
        try {
            if (a == null || b == null) {
                log.warning("Null parameters received for subtraction");
                return BigDecimal.ZERO;
            }
            
            return calculatorService.subtract(a, b);
            
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error performing subtraction via SOAP", e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        log.info("SOAP: Multiplying " + a + " * " + b);
        
        try {
            if (a == null || b == null) {
                log.warning("Null parameters received for multiplication");
                return BigDecimal.ZERO;
            }
            
            return calculatorService.multiply(a, b);
            
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error performing multiplication via SOAP", e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String divide(BigDecimal a, BigDecimal b, int scale) {
        log.info("SOAP: Dividing " + a + " / " + b + " with scale " + scale);
        
        try {
            if (a == null || b == null) {
                return "ERROR: Null parameters received";
            }
            
            if (b.compareTo(BigDecimal.ZERO) == 0) {
                return "ERROR: Cannot divide by zero";
            }
            
            if (scale < 0) {
                scale = 2; // Default scale
            }
            
            BigDecimal result = calculatorService.divide(a, b, scale);
            return result.toString();
            
        } catch (ArithmeticException e) {
            log.log(Level.WARNING, "Arithmetic error during division", e);
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error performing division via SOAP", e);
            return "ERROR: Unexpected error during division";
        }
    }

    @Override
    public BigDecimal calculatePercentage(BigDecimal value, BigDecimal percentage) {
        log.info("SOAP: Calculating " + percentage + "% of " + value);
        
        try {
            if (value == null || percentage == null) {
                log.warning("Null parameters received for percentage calculation");
                return BigDecimal.ZERO;
            }
            
            return calculatorService.calculatePercentage(value, percentage);
            
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error calculating percentage via SOAP", e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal calculateCompoundInterest(BigDecimal principal, BigDecimal rate, 
                                                int timesCompounded, int years) {
        log.info("SOAP: Calculating compound interest for principal: " + principal + 
                 ", rate: " + rate + "%, times: " + timesCompounded + ", years: " + years);
        
        try {
            if (principal == null || rate == null) {
                log.warning("Null parameters received for compound interest calculation");
                return BigDecimal.ZERO;
            }
            
            if (principal.compareTo(BigDecimal.ZERO) <= 0) {
                log.warning("Principal must be greater than zero");
                return BigDecimal.ZERO;
            }
            
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                log.warning("Rate cannot be negative");
                return BigDecimal.ZERO;
            }
            
            if (timesCompounded <= 0) {
                log.warning("Times compounded must be greater than zero");
                return BigDecimal.ZERO;
            }
            
            if (years <= 0) {
                log.warning("Years must be greater than zero");
                return BigDecimal.ZERO;
            }
            
            return calculatorService.calculateCompoundInterest(principal, rate, 
                                                              timesCompounded, years);
            
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error calculating compound interest via SOAP", e);
            return BigDecimal.ZERO;
        }
    }
}
