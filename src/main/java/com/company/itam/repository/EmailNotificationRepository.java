package com.company.itam.repository;

import com.company.itam.model.EmailNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    List<EmailNotification> findAllByOrderBySentAtDesc();

    List<EmailNotification> findByRecipientEmailIgnoreCaseOrderBySentAtDesc(String recipientEmail);
}
