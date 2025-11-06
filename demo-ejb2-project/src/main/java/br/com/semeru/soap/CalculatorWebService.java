package br.com.semeru.soap;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import java.math.BigDecimal;

/**
 * SOAP Web Service Interface for Calculator operations
 * This interface defines the contract for mathematical calculation operations
 */
@WebService(name = "CalculatorWebService", targetNamespace = "http://soap.semeru.com.br/")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface CalculatorWebService {

    /**
     * Add two numbers
     * 
     * @param a First number
     * @param b Second number
     * @return Sum of a and b
     */
    @WebMethod(operationName = "add")
    @WebResult(name = "result")
    BigDecimal add(
            @WebParam(name = "a") BigDecimal a,
            @WebParam(name = "b") BigDecimal b
    );

    /**
     * Subtract two numbers
     * 
     * @param a First number
     * @param b Second number
     * @return Difference of a and b
     */
    @WebMethod(operationName = "subtract")
    @WebResult(name = "result")
    BigDecimal subtract(
            @WebParam(name = "a") BigDecimal a,
            @WebParam(name = "b") BigDecimal b
    );

    /**
     * Multiply two numbers
     * 
     * @param a First number
     * @param b Second number
     * @return Product of a and b
     */
    @WebMethod(operationName = "multiply")
    @WebResult(name = "result")
    BigDecimal multiply(
            @WebParam(name = "a") BigDecimal a,
            @WebParam(name = "b") BigDecimal b
    );

    /**
     * Divide two numbers
     * 
     * @param a Dividend
     * @param b Divisor
     * @param scale Number of decimal places
     * @return Quotient of a and b
     */
    @WebMethod(operationName = "divide")
    @WebResult(name = "result")
    String divide(
            @WebParam(name = "a") BigDecimal a,
            @WebParam(name = "b") BigDecimal b,
            @WebParam(name = "scale") int scale
    );

    /**
     * Calculate percentage of a value
     * 
     * @param value The base value
     * @param percentage The percentage to calculate
     * @return Percentage of the value
     */
    @WebMethod(operationName = "calculatePercentage")
    @WebResult(name = "result")
    BigDecimal calculatePercentage(
            @WebParam(name = "value") BigDecimal value,
            @WebParam(name = "percentage") BigDecimal percentage
    );

    /**
     * Calculate compound interest
     * Formula: A = P(1 + r/n)^(nt)
     * 
     * @param principal Initial amount
     * @param rate Interest rate (as percentage)
     * @param timesCompounded Number of times interest is compounded per year
     * @param years Number of years
     * @return Final amount after compound interest
     */
    @WebMethod(operationName = "calculateCompoundInterest")
    @WebResult(name = "result")
    BigDecimal calculateCompoundInterest(
            @WebParam(name = "principal") BigDecimal principal,
            @WebParam(name = "rate") BigDecimal rate,
            @WebParam(name = "timesCompounded") int timesCompounded,
            @WebParam(name = "years") int years
    );
}
