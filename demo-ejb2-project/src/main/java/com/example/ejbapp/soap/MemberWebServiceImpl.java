package com.example.ejbapp.soap;

import com.example.ejbapp.data.MemberRepository;
import com.example.ejbapp.model.Member;
import com.example.ejbapp.service.MemberRegistration;
import com.example.ejbapp.soap.dto.MemberListResponse;
import com.example.ejbapp.soap.dto.MemberRequest;
import com.example.ejbapp.soap.dto.MemberResponse;

import javax.inject.Inject;
import javax.jws.WebService;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SOAP Web Service Implementation for Member operations
 * 
 * This implementation provides SOAP endpoints for member management including
 * registration, retrieval, update, and deletion operations.
 * 
 * WSDL available at: http://localhost:8080/demo-ejb2-project/MemberWebService?wsdl
 */
@WebService(
    serviceName = "MemberWebService",
    portName = "MemberWebServicePort",
    endpointInterface = "com.example.ejbapp.soap.MemberWebService",
    targetNamespace = "http://soap.example.com/"
)
public class MemberWebServiceImpl implements MemberWebService {

    @Inject
    private Logger log;

    @Inject
    private MemberRepository memberRepository;

    @Inject
    private MemberRegistration memberRegistration;

    @Inject
    private EntityManager em;

    @Override
    public MemberResponse registerMember(MemberRequest request) {
        log.info("SOAP: Registering new member - " + request.getName());

        try {
            // Validate request
            if (request == null) {
                return new MemberResponse(false, "Member request cannot be null");
            }

            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return new MemberResponse(false, "Member name is required");
            }

            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return new MemberResponse(false, "Member email is required");
            }

            if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
                return new MemberResponse(false, "Member phone number is required");
            }

            // Create member entity
            Member member = new Member();
            member.setName(request.getName());
            member.setEmail(request.getEmail());
            member.setPhoneNumber(request.getPhoneNumber());

            // Register member
            memberRegistration.register(member);

            // Return success response
            return new MemberResponse(
                true,
                "Member registered successfully",
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber()
            );

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error registering member via SOAP", e);
            return new MemberResponse(false, "Failed to register member: " + e.getMessage());
        }
    }

    @Override
    public MemberResponse getMemberById(Long memberId) {
        log.info("SOAP: Getting member by ID - " + memberId);

        try {
            if (memberId == null) {
                return new MemberResponse(false, "Member ID is required");
            }

            Member member = memberRepository.findById(memberId);

            if (member == null) {
                return new MemberResponse(false, "Member not found with ID: " + memberId);
            }

            return new MemberResponse(
                true,
                "Member found",
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber()
            );

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error getting member by ID via SOAP", e);
            return new MemberResponse(false, "Failed to get member: " + e.getMessage());
        }
    }

    @Override
    public MemberResponse getMemberByEmail(String email) {
        log.info("SOAP: Getting member by email - " + email);

        try {
            if (email == null || email.trim().isEmpty()) {
                return new MemberResponse(false, "Email is required");
            }

            Member member = memberRepository.findByEmail(email);

            if (member == null) {
                return new MemberResponse(false, "Member not found with email: " + email);
            }

            return new MemberResponse(
                true,
                "Member found",
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber()
            );

        } catch (NoResultException e) {
            return new MemberResponse(false, "Member not found with email: " + email);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error getting member by email via SOAP", e);
            return new MemberResponse(false, "Failed to get member: " + e.getMessage());
        }
    }

    @Override
    public MemberListResponse getAllMembers() {
        log.info("SOAP: Getting all members");

        try {
            List<Member> members = memberRepository.findAllOrderedByName();
            List<MemberResponse> memberResponses = new ArrayList<>();

            for (Member member : members) {
                MemberResponse memberResponse = new MemberResponse(
                    true,
                    "Member data",
                    member.getId(),
                    member.getName(),
                    member.getEmail(),
                    member.getPhoneNumber()
                );
                memberResponses.add(memberResponse);
            }

            return new MemberListResponse(
                true,
                "Found " + memberResponses.size() + " member(s)",
                memberResponses
            );

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error getting all members via SOAP", e);
            return new MemberListResponse(false, "Failed to get members: " + e.getMessage());
        }
    }

    @Override
    public MemberResponse updateMember(Long memberId, MemberRequest request) {
        log.info("SOAP: Updating member - " + memberId);

        try {
            // Validate request
            if (memberId == null) {
                return new MemberResponse(false, "Member ID is required");
            }

            if (request == null) {
                return new MemberResponse(false, "Member request cannot be null");
            }

            // Find existing member
            Member member = memberRepository.findById(memberId);

            if (member == null) {
                return new MemberResponse(false, "Member not found with ID: " + memberId);
            }

            // Update member fields
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                member.setName(request.getName());
            }

            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                member.setEmail(request.getEmail());
            }

            if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
                member.setPhoneNumber(request.getPhoneNumber());
            }

            // Persist changes
            em.merge(member);
            em.flush();

            return new MemberResponse(
                true,
                "Member updated successfully",
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber()
            );

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error updating member via SOAP", e);
            return new MemberResponse(false, "Failed to update member: " + e.getMessage());
        }
    }

    @Override
    public MemberResponse deleteMember(Long memberId) {
        log.info("SOAP: Deleting member - " + memberId);

        try {
            if (memberId == null) {
                return new MemberResponse(false, "Member ID is required");
            }

            Member member = memberRepository.findById(memberId);

            if (member == null) {
                return new MemberResponse(false, "Member not found with ID: " + memberId);
            }

            // Delete member
            em.remove(em.contains(member) ? member : em.merge(member));
            em.flush();

            return new MemberResponse(
                true,
                "Member deleted successfully with ID: " + memberId
            );

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error deleting member via SOAP", e);
            return new MemberResponse(false, "Failed to delete member: " + e.getMessage());
        }
    }
}
