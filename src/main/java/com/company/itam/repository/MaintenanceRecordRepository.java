package com.company.itam.repository;

import com.company.itam.model.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.asset.id = :assetId ORDER BY m.serviceDate DESC")
    List<MaintenanceRecord> findByAssetIdOrderByServiceDateDesc(@Param("assetId") Long assetId);

    @Query("SELECT m FROM MaintenanceRecord m WHERE m.asset.id IN :assetIds ORDER BY m.serviceDate DESC")
    List<MaintenanceRecord> findByAssetIdInOrderByServiceDateDesc(@Param("assetIds") List<Long> assetIds);

    List<MaintenanceRecord> findAllByOrderByServiceDateDesc();
}
