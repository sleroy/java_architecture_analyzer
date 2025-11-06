package br.com.semeru.soap.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

/**
 * Member Response DTO for SOAP Web Service
 * Used for returning member information and operation results
 */
@XmlRootElement(name = "MemberResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MemberResponse", propOrder = {
    "success",
    "message",
    "memberId",
    "name",
    "email",
    "phoneNumber"
})
public class MemberResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(required = true)
    private boolean success;

    @XmlElement
    private String message;

    @XmlElement
    private Long memberId;

    @XmlElement
    private String name;

    @XmlElement
    private String email;

    @XmlElement
    private String phoneNumber;

    public MemberResponse() {
    }

    public MemberResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public MemberResponse(boolean success, String message, Long memberId, String name, String email, String phoneNumber) {
        this.success = success;
        this.message = message;
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String toString() {
        return "MemberResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", memberId=" + memberId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
