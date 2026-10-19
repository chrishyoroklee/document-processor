package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;
import java.time.Instant;

import dev.hyorok.docproc.document.Document;
import dev.hyorok.docproc.document.DocumentStatus;
import dev.hyorok.docproc.document.DocumentType;

/**
 * One row in the document list. Deliberately has no line items: reading them
 * for every document would trigger one extra query per row (the N+1 problem).
 */
public record DocumentSummary(
        Long id,
        String fileName,
        DocumentType documentType,
        DocumentStatus status,
        String vendorName,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt) {

    public static DocumentSummary from(Document doc) {
        return new DocumentSummary(
                doc.getId(),
                doc.getFileName(),
                doc.getDocumentType(),
                doc.getStatus(),
                doc.getVendorName(),
                doc.getTotalAmount(),
                doc.getCurrency(),
                doc.getCreatedAt());
    }
}
