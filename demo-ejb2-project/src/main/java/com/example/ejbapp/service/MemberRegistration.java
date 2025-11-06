package com.example.ejbapp.service;

import com.example.ejbapp.model.Member;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Service
@Transactional
public class MemberRegistration {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistration.class);

    @PersistenceContext
    private EntityManager em;

    private final ApplicationEventPublisher eventPublisher;

    public MemberRegistration(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void register(Member member) throws Exception {
        log.info("Registering " + member.getName());
        em.persist(member);
        eventPublisher.publishEvent(member);
    }
}
