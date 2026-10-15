package com.company.itam.service;

import com.company.itam.dto.TicketRequestDto;
import com.company.itam.dto.TicketStatsDto;
import com.company.itam.dto.TicketUpdateDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.*;
import com.company.itam.repository.AssetRepository;
import com.company.itam.repository.TicketRepository;
import com.company.itam.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    public TicketService(TicketRepository ticketRepository,
                         AssetRepository assetRepository,
                         UserRepository userRepository,
                         EmailService emailService,
                         ActivityLogService activityLogService) {
        this.ticketRepository = ticketRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    public Ticket createTicket(TicketRequestDto dto) {
        Ticket ticket = new Ticket();
        ticket.setTicketNumber(generateNextTicketNumber());
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setCategory(dto.getCategory() != null ? dto.getCategory() : TicketCategory.HARDWARE);
        ticket.setPriority(dto.getPriority() != null ? dto.getPriority() : TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setRequesterName(dto.getRequesterName());
        ticket.setRequesterEmail(dto.getRequesterEmail().toLowerCase().trim());
        ticket.setAssignedTo(dto.getAssignedTo() != null ? dto.getAssignedTo() : "Dhiraj Shinde");

        String dept = dto.getRequesterDepartment();
        if (dept == null || dept.isBlank()) {
            Optional<AppUser> userOpt = userRepository.findByEmailIgnoreCase(ticket.getRequesterEmail());
            if (userOpt.isPresent()) {
                dept = userOpt.get().getDepartment();
            }
        }
        ticket.setRequesterDepartment(dept);

        if (dto.getAssetId() != null) {
            assetRepository.findById(dto.getAssetId()).ifPresent(ticket::setAsset);
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created IT support ticket: {} - '{}' by {}", saved.getTicketNumber(), saved.getTitle(), saved.getRequesterEmail());

        activityLogService.log(
                ActivityAction.CREATED,
                ItemType.TICKET,
                saved.getId(),
                saved.getTicketNumber(),
                saved.getTitle(),
                saved.getRequesterName(),
                saved.getRequesterEmail(),
                "Helpdesk / Portal",
                "Created IT ticket [" + saved.getCategory() + " / " + saved.getPriority() + "]"
        );

        // 1. Dispatch confirmation email to requester
        try {
            emailService.sendTicketCreatedNotification(saved);
        } catch (Exception e) {
            log.warn("Could not dispatch ticket created email to requester {}: {}", saved.getRequesterEmail(), e.getMessage());
        }

        // 2. Dispatch alert email to assigned technician
        try {
            TechnicianInfo tech = resolveTechnician(saved.getAssignedTo());
            emailService.sendTicketTechnicianAlert(saved, tech.email(), tech.name());
            log.info("Dispatched technician alert email for ticket {} to {} ({})", saved.getTicketNumber(), tech.name(), tech.email());
        } catch (Exception e) {
            log.warn("Could not dispatch technician alert email for ticket {}: {}", saved.getTicketNumber(), e.getMessage());
        }

        return saved;
    }

    public Ticket updateTicket(Long id, TicketUpdateDto dto, String updatedBy) {
        Ticket ticket = getTicketById(id);
        TicketStatus oldStatus = ticket.getStatus();
        String oldAssignedTo = ticket.getAssignedTo();

        if (dto.getStatus() != null) {
            ticket.setStatus(dto.getStatus());
            if ((dto.getStatus() == TicketStatus.RESOLVED || dto.getStatus() == TicketStatus.CLOSED) && ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(LocalDateTime.now());
            } else if (dto.getStatus() == TicketStatus.OPEN || dto.getStatus() == TicketStatus.IN_PROGRESS) {
                ticket.setResolvedAt(null);
            }
        }
        if (dto.getPriority() != null) {
            ticket.setPriority(dto.getPriority());
        }
        if (dto.getCategory() != null) {
            ticket.setCategory(dto.getCategory());
        }

        boolean reassigned = false;
        if (dto.getAssignedTo() != null && !dto.getAssignedTo().isBlank()) {
            String cleanOld = (oldAssignedTo != null) ? oldAssignedTo.trim() : "";
            String cleanNew = dto.getAssignedTo().trim();
            if (!cleanNew.equalsIgnoreCase(cleanOld)) {
                ticket.setAssignedTo(cleanNew);
                reassigned = true;
            }
        }
        if (dto.getResolutionNotes() != null) {
            ticket.setResolutionNotes(dto.getResolutionNotes());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Updated ticket {} to status {}", saved.getTicketNumber(), saved.getStatus());

        activityLogService.log(
                ActivityAction.UPDATED,
                ItemType.TICKET,
                saved.getId(),
                saved.getTicketNumber(),
                saved.getTitle(),
                saved.getRequesterName(),
                saved.getRequesterEmail(),
                updatedBy != null ? updatedBy : "IT Admin",
                "Status: " + oldStatus + " -> " + saved.getStatus() + (reassigned ? (", Reassigned to: " + saved.getAssignedTo()) : "")
        );

        if (oldStatus != saved.getStatus()) {
            try {
                emailService.sendTicketUpdatedNotification(saved);
            } catch (Exception e) {
                log.warn("Could not dispatch ticket status update email: {}", e.getMessage());
            }
        }

        if (reassigned) {
            try {
                TechnicianInfo tech = resolveTechnician(saved.getAssignedTo());
                emailService.sendTicketReassignedToTechnicianNotification(saved, tech.email(), tech.name(), updatedBy);
                log.info("Dispatched ticket reassignment notification for {} to {} ({})", saved.getTicketNumber(), tech.name(), tech.email());
            } catch (Exception e) {
                log.warn("Could not dispatch technician reassignment notification: {}", e.getMessage());
            }
        }

        return saved;
    }

    public record TechnicianInfo(String email, String name) {}

    public TechnicianInfo resolveTechnician(String assignedTo) {
        String email = null;
        String name = (assignedTo != null && !assignedTo.isBlank()) ? assignedTo.trim() : "IT Support";

        if (assignedTo != null && !assignedTo.isBlank()) {
            String trimmed = assignedTo.trim();
            if (trimmed.contains("@")) {
                email = trimmed.toLowerCase();
                Optional<AppUser> user = userRepository.findByEmailIgnoreCase(email);
                if (user.isPresent()) {
                    name = user.get().getFullName();
                }
            } else {
                Optional<AppUser> user = userRepository.findFirstByFullNameIgnoreCase(trimmed);
                if (user.isEmpty()) {
                    List<AppUser> matches = userRepository.findBestMatchByName(trimmed);
                    if (!matches.isEmpty()) {
                        user = Optional.of(matches.get(0));
                    }
                }
                if (user.isPresent()) {
                    email = user.get().getEmail();
                    name = user.get().getFullName();
                }
            }
        }

        // Fallback: if not resolved, lookup primary admin dhiraj@bmconsulting.co or first ADMIN user
        if (email == null || email.isBlank()) {
            Optional<AppUser> dhiraj = userRepository.findByEmailIgnoreCase("dhiraj@bmconsulting.co");
            if (dhiraj.isPresent()) {
                email = dhiraj.get().getEmail();
                if (assignedTo == null || assignedTo.isBlank() || assignedTo.equalsIgnoreCase("Dhiraj Shinde")) {
                    name = dhiraj.get().getFullName();
                }
            } else {
                List<AppUser> admins = userRepository.findByRole(UserRole.ADMIN);
                if (!admins.isEmpty()) {
                    email = admins.get(0).getEmail();
                    if (assignedTo == null || assignedTo.isBlank()) {
                        name = admins.get(0).getFullName();
                    }
                } else {
                    email = "dhiraj@bmconsulting.co";
                    name = "Dhiraj Shinde";
                }
            }
        }

        return new TechnicianInfo(email, name);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String query, TicketStatus status, TicketPriority priority, TicketCategory category) {
        String cleanQ = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        return ticketRepository.searchTickets(cleanQ, status, priority, category);
    }

    @Transactional(readOnly = true)
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Ticket getTicketByNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with number: " + ticketNumber));
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByRequesterEmail(String email) {
        return ticketRepository.findByRequesterEmailIgnoreCaseOrderByCreatedAtDesc(email);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getMyTickets(String email) {
        return getTicketsByRequesterEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByAssetId(Long assetId) {
        return ticketRepository.findByAssetIdOrderByCreatedAtDesc(assetId);
    }

    @Transactional(readOnly = true)
    public TicketStatsDto getTicketStats() {
        long total = ticketRepository.count();
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long pendingParts = ticketRepository.countByStatus(TicketStatus.PENDING_PARTS);
        long resolved = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        long closed = ticketRepository.countByStatus(TicketStatus.CLOSED);
        long critical = ticketRepository.countByPriority(TicketPriority.CRITICAL);
        return new TicketStatsDto(total, open, inProgress, pendingParts, resolved, closed, critical);
    }

    public void deleteTicket(Long id) {
        Ticket ticket = getTicketById(id);
        ticketRepository.delete(ticket);
        activityLogService.log(
                ActivityAction.DELETED,
                ItemType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getRequesterName(),
                ticket.getRequesterEmail(),
                "IT Admin",
                "Deleted ticket from system"
        );
    }

    private synchronized String generateNextTicketNumber() {
        long nextVal = ticketRepository.count() + 1001;
        String candidate = "TICK-" + nextVal;
        while (ticketRepository.existsByTicketNumber(candidate)) {
            nextVal++;
            candidate = "TICK-" + nextVal;
        }
        return candidate;
    }
}
