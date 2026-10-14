package com.company.itam.repository;

import com.company.itam.model.SoftwareLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, Long> {
    List<SoftwareLicense> findByDepartmentIgnoreCase(String department);

    @org.springframework.data.jpa.repository.Query("SELECT l FROM SoftwareLicense l WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(l.softwareName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(l.vendor) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(l.licenseKey) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(l.licenseType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(l.department) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<SoftwareLicense> searchLicenses(@org.springframework.data.repository.query.Param("query") String query);
}
