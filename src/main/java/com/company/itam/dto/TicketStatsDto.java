package com.company.itam.dto;

public class TicketStatsDto {

    private long total;
    private long open;
    private long inProgress;
    private long pendingParts;
    private long resolved;
    private long closed;
    private long critical;

    public TicketStatsDto() {}

    public TicketStatsDto(long total, long open, long inProgress, long pendingParts, long resolved, long closed, long critical) {
        this.total = total;
        this.open = open;
        this.inProgress = inProgress;
        this.pendingParts = pendingParts;
        this.resolved = resolved;
        this.closed = closed;
        this.critical = critical;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getOpen() {
        return open;
    }

    public void setOpen(long open) {
        this.open = open;
    }

    public long getInProgress() {
        return inProgress;
    }

    public void setInProgress(long inProgress) {
        this.inProgress = inProgress;
    }

    public long getPendingParts() {
        return pendingParts;
    }

    public void setPendingParts(long pendingParts) {
        this.pendingParts = pendingParts;
    }

    public long getResolved() {
        return resolved;
    }

    public void setResolved(long resolved) {
        this.resolved = resolved;
    }

    public long getClosed() {
        return closed;
    }

    public void setClosed(long closed) {
        this.closed = closed;
    }

    public long getCritical() {
        return critical;
    }

    public void setCritical(long critical) {
        this.critical = critical;
    }
}
