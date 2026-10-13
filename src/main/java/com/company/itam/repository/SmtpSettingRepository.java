package com.company.itam.repository;

import com.company.itam.model.SmtpSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmtpSettingRepository extends JpaRepository<SmtpSetting, Long> {
    Optional<SmtpSetting> findFirstByOrderByIdAsc();
}
