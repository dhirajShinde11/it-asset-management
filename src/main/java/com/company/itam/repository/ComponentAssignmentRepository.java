package com.company.itam.repository;

import com.company.itam.model.ComponentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentAssignmentRepository extends JpaRepository<ComponentAssignment, Long> {
    List<ComponentAssignment> findByComponent_Id(Long componentId);
    List<ComponentAssignment> findByAssetId(Long assetId);
}
