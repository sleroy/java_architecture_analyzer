package com.example.ejbapp.soap.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

/**
 * Member Request DTO for SOAP Web Service
 * Used for creating and updating member information
 */
@XmlRootElement(name = "MemberRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MemberRequest", propOrder = {
    "name",
    "email",
    "phoneNumber"
})
public class MemberRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @XmlElement(required = true)
    private String name;

    @XmlElement(required = true)
    private String email;

    @XmlElement(required = true)
    private String phoneNumber;

    public MemberRequest() {
    }

    public MemberRequest(String name, String email, String phoneNumber) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
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
        return "MemberRequest{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
