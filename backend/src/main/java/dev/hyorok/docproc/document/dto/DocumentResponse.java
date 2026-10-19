package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import dev.hyorok.docproc.document.Document;
import dev.hyorok.docproc.document.DocumentStatus;
import dev.hyorok.docproc.document.DocumentType;

/** Full detail of one document, including its line items. */
public record DocumentResponse(
        Long id,
        String fileName,
        DocumentType documentType,
        DocumentStatus status,
        String vendorName,
        String documentNumber,
        LocalDate documentDate,
        String currency,
        BigDecimal totalAmount,
        List<LineItemResponse> lineItems,
        List<AppliedCorrectionResponse> appliedCorrections,
        Instant createdAt,
        Instant updatedAt) {

    /** Must be called inside a transaction: it reads the lazy line item and correction collections. */
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getFileName(),
                doc.getDocumentType(),
                doc.getStatus(),
                doc.getVendorName(),
                doc.getDocumentNumber(),
                doc.getDocumentDate(),
                doc.getCurrency(),
                doc.getTotalAmount(),
                doc.getLineItems().stream().map(LineItemResponse::from).toList(),
                doc.getAppliedCorrections().stream()
                        .map(AppliedCorrectionResponse::from)
                        .sorted(Comparator.comparing(AppliedCorrectionResponse::id))
                        .toList(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }
}
