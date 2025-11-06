package com.example.ejbapp.soap.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Member List Response DTO for SOAP Web Service
 * Used for returning a list of members
 */
@XmlRootElement(name = "MemberListResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MemberListResponse", propOrder = {
    "success",
    "message",
    "members"
})
public class MemberListResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(required = true)
    private boolean success;

    @XmlElement
    private String message;

    @XmlElement
    private List<MemberResponse> members;

    public MemberListResponse() {
        this.members = new ArrayList<>();
    }

    public MemberListResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.members = new ArrayList<>();
    }

    public MemberListResponse(boolean success, String message, List<MemberResponse> members) {
        this.success = success;
        this.message = message;
        this.members = members != null ? members : new ArrayList<>();
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

    public List<MemberResponse> getMembers() {
        return members;
    }

    public void setMembers(List<MemberResponse> members) {
        this.members = members;
    }

    @Override
    public String toString() {
        return "MemberListResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", membersCount=" + (members != null ? members.size() : 0) +
                '}';
    }
}
