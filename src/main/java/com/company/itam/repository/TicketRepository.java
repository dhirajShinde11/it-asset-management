package com.company.itam.repository;

import com.company.itam.model.Ticket;
import com.company.itam.model.TicketCategory;
import com.company.itam.model.TicketPriority;
import com.company.itam.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findTopByOrderByIdDesc();

    boolean existsByTicketNumber(String ticketNumber);

    List<Ticket> findByRequesterEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<Ticket> findByAssetIdOrderByCreatedAtDesc(Long assetId);

    long countByStatus(TicketStatus status);

    long countByPriority(TicketPriority priority);

    @Query("SELECT t FROM Ticket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.requesterDepartment) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.assignedTo) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "(t.asset IS NOT NULL AND (LOWER(t.asset.assetTag) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.asset.name) LIKE LOWER(CONCAT('%', :query, '%'))))) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> searchTickets(
            @Param("query") String query,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("category") TicketCategory category);

    @Query("SELECT t FROM Ticket t WHERE " +
           "LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findTop10ByQuickSearch(@Param("query") String query);
}
