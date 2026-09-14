package kr.releasepilot.controlplane.audit;

public interface AuditArchiveSink { void archive(AuditEvent event); }
