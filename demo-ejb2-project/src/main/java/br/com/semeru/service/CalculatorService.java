package br.com.semeru.service;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;

/**
 * Stateless bean example for mathematical calculations.
 * Demonstrates a simple service without database operations.
 */
@Stateless
public class CalculatorService {

    @Inject
    private Logger log;

    /**
     * Adds two numbers
     */
    public BigDecimal add(BigDecimal a, BigDecimal b) {
        log.info("Adding " + a + " + " + b);
        return a.add(b);
    }

    /**
     * Subtracts two numbers
     */
    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        log.info("Subtracting " + a + " - " + b);
        return a.subtract(b);
    }

    /**
     * Multiplies two numbers
     */
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        log.info("Multiplying " + a + " * " + b);
        return a.multiply(b);
    }

    /**
     * Divides two numbers with specified scale and rounding mode
     */
    public BigDecimal divide(BigDecimal a, BigDecimal b, int scale) throws ArithmeticException {
        if (b.compareTo(BigDecimal.ZERO) == 0) {
            log.severe("Division by zero attempted");
            throw new ArithmeticException("Cannot divide by zero");
        }
        log.info("Dividing " + a + " / " + b);
        return a.divide(b, scale, RoundingMode.HALF_UP);
    }

    /**
     * Calculates percentage
     */
    public BigDecimal calculatePercentage(BigDecimal value, BigDecimal percentage) {
        log.info("Calculating " + percentage + "% of " + value);
        return value.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates compound interest
     * Formula: A = P(1 + r/n)^(nt)
     */
    public BigDecimal calculateCompoundInterest(BigDecimal principal, BigDecimal rate, 
                                                int timesCompounded, int years) {
        log.info("Calculating compound interest for principal: " + principal);
        
        BigDecimal n = new BigDecimal(timesCompounded);
        BigDecimal t = new BigDecimal(years);
        BigDecimal r = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        
        BigDecimal ratePerPeriod = r.divide(n, 10, RoundingMode.HALF_UP);
        BigDecimal onePlusRate = BigDecimal.ONE.add(ratePerPeriod);
        BigDecimal exponent = n.multiply(t);
        
        double power = Math.pow(onePlusRate.doubleValue(), exponent.doubleValue());
        BigDecimal amount = principal.multiply(new BigDecimal(power));
        
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
