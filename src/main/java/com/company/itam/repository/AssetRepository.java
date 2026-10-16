package com.company.itam.repository;

import com.company.itam.model.Asset;
import com.company.itam.model.AssetCategory;
import com.company.itam.model.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetTag(String assetTag);

    boolean existsByAssetTag(String assetTag);

    List<Asset> findByStatus(AssetStatus status);

    List<Asset> findByCategory(AssetCategory category);

    List<Asset> findByAssignedDepartmentIgnoreCase(String department);

    List<Asset> findByAssignedEmployeeEmailIgnoreCase(String email);

    @Query("SELECT a FROM Asset a WHERE " +
           "(a.assignedEmployeeEmail IS NOT NULL AND LOWER(a.assignedEmployeeEmail) = LOWER(:email)) OR " +
           "(:name IS NOT NULL AND LOWER(a.assignedEmployeeName) = LOWER(:name))")
    List<Asset> findByAssignedEmployeeEmailOrName(@Param("email") String email, @Param("name") String name);

    long countByAssignedEmployeeEmailIgnoreCase(String email);

    List<Asset> findByWarrantyExpiryDateBetween(LocalDate start, LocalDate end);

    long countByWarrantyExpiryDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(a.purchaseCost), 0) FROM Asset a")
    BigDecimal sumTotalAssetValue();

    @Query("SELECT a.category, COUNT(a) FROM Asset a GROUP BY a.category")
    List<Object[]> countGroupByCategory();

    @Query("SELECT a.assignedDepartment, COUNT(a) FROM Asset a WHERE a.assignedDepartment IS NOT NULL AND TRIM(a.assignedDepartment) <> '' GROUP BY a.assignedDepartment")
    List<Object[]> countGroupByDepartment();

    @Query("SELECT a FROM Asset a WHERE (a.assignedEmployeeEmail IS NOT NULL AND TRIM(a.assignedEmployeeEmail) <> '') OR (a.assignedEmployeeName IS NOT NULL AND TRIM(a.assignedEmployeeName) <> '')")
    List<Asset> findAllAssignedAssets();

    @Query("SELECT a FROM Asset a WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.model) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedEmployeeName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedEmployeeEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedDepartment) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.location) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:category IS NULL OR a.category = :category) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:department IS NULL OR :department = '' OR LOWER(a.assignedDepartment) = LOWER(:department)) " +
           "ORDER BY a.id DESC")
    List<Asset> filterAssets(
            @Param("query") String query,
            @Param("category") AssetCategory category,
            @Param("status") AssetStatus status,
            @Param("department") String department);

    @Query("SELECT a FROM Asset a WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.model) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedEmployeeName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedEmployeeEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.assignedDepartment) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.location) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Asset> searchAssets(@Param("query") String query);

    long countByStatus(AssetStatus status);

    long countByCategory(AssetCategory category);
}
