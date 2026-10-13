package com.company.itam.repository;

import com.company.itam.model.SoftwareLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, Long> {
    List<SoftwareLicense> findByDepartmentIgnoreCase(String department);
}
