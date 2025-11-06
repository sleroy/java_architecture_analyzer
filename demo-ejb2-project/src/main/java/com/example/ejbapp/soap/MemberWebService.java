package com.example.ejbapp.soap;

import com.example.ejbapp.soap.dto.MemberRequest;
import com.example.ejbapp.soap.dto.MemberResponse;
import com.example.ejbapp.soap.dto.MemberListResponse;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

/**
 * SOAP Web Service Interface for Member operations
 * This interface defines the contract for the Member SOAP web service
 */
@WebService(name = "MemberWebService", targetNamespace = "http://soap.ejbapp.example.com/")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface MemberWebService {

    /**
     * Register a new member
     * 
     * @param request MemberRequest containing member details
     * @return MemberResponse with registration result
     */
    @WebMethod(operationName = "registerMember")
    @WebResult(name = "memberResponse")
    MemberResponse registerMember(
            @WebParam(name = "memberRequest") MemberRequest request
    );

    /**
     * Get member by ID
     * 
     * @param memberId The ID of the member to retrieve
     * @return MemberResponse with member details
     */
    @WebMethod(operationName = "getMemberById")
    @WebResult(name = "memberResponse")
    MemberResponse getMemberById(
            @WebParam(name = "memberId") Long memberId
    );

    /**
     * Get member by email
     * 
     * @param email The email of the member to retrieve
     * @return MemberResponse with member details
     */
    @WebMethod(operationName = "getMemberByEmail")
    @WebResult(name = "memberResponse")
    MemberResponse getMemberByEmail(
            @WebParam(name = "email") String email
    );

    /**
     * Get all members
     * 
     * @return MemberListResponse containing all members
     */
    @WebMethod(operationName = "getAllMembers")
    @WebResult(name = "memberListResponse")
    MemberListResponse getAllMembers();

    /**
     * Update member information
     * 
     * @param memberId The ID of the member to update
     * @param request MemberRequest with updated information
     * @return MemberResponse with update result
     */
    @WebMethod(operationName = "updateMember")
    @WebResult(name = "memberResponse")
    MemberResponse updateMember(
            @WebParam(name = "memberId") Long memberId,
            @WebParam(name = "memberRequest") MemberRequest request
    );

    /**
     * Delete member by ID
     * 
     * @param memberId The ID of the member to delete
     * @return MemberResponse with deletion result
     */
    @WebMethod(operationName = "deleteMember")
    @WebResult(name = "memberResponse")
    MemberResponse deleteMember(
            @WebParam(name = "memberId") Long memberId
    );
}
