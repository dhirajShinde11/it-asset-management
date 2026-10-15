package com.company.itam.controller;

import com.company.itam.dto.TicketRequestDto;
import com.company.itam.dto.TicketStatsDto;
import com.company.itam.dto.TicketUpdateDto;
import com.company.itam.model.Ticket;
import com.company.itam.model.TicketCategory;
import com.company.itam.model.TicketPriority;
import com.company.itam.model.TicketStatus;
import com.company.itam.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "IT Helpdesk & Tickets", description = "Endpoints for IT support tickets, issue reporting, and resolution tracking")
@CrossOrigin(origins = "*")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Get all IT support tickets with optional search, status, priority, and category filters")
    public ResponseEntity<List<Ticket>> getAllTickets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category) {
        return ResponseEntity.ok(ticketService.getAllTickets(query, status, priority, category));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get aggregate statistics for IT tickets dashboard")
    public ResponseEntity<TicketStatsDto> getTicketStats() {
        return ResponseEntity.ok(ticketService.getTicketStats());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket details by ID")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    @GetMapping("/number/{ticketNumber}")
    @Operation(summary = "Get ticket details by Ticket Number")
    public ResponseEntity<Ticket> getTicketByNumber(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(ticketService.getTicketByNumber(ticketNumber));
    }

    @GetMapping("/my")
    @Operation(summary = "Get all tickets submitted by a specific employee email")
    public ResponseEntity<List<Ticket>> getMyTickets(@RequestParam String email) {
        return ResponseEntity.ok(ticketService.getTicketsByRequesterEmail(email));
    }

    @GetMapping("/asset/{assetId}")
    @Operation(summary = "Get all tickets associated with a hardware asset")
    public ResponseEntity<List<Ticket>> getAssetTickets(@PathVariable Long assetId) {
        return ResponseEntity.ok(ticketService.getTicketsByAssetId(assetId));
    }

    @PostMapping
    @Operation(summary = "Create a new IT support ticket")
    public ResponseEntity<Ticket> createTicket(@Valid @RequestBody TicketRequestDto dto) {
        Ticket created = ticketService.createTicket(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ticket status, priority, assigned technician, or resolution notes")
    public ResponseEntity<Ticket> updateTicket(
            @PathVariable Long id,
            @RequestBody TicketUpdateDto dto,
            @RequestParam(required = false, defaultValue = "IT Admin") String updatedBy) {
        return ResponseEntity.ok(ticketService.updateTicket(id, dto, updatedBy));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an IT support ticket")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
