package dev.hyorok.docproc.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.hyorok.docproc.document.DocumentType;

/**
 * What Claude found in a document. Component names match the JSON schema in
 * extraction-schema.json exactly, so Jackson maps the reply with no annotations.
 * Any field except documentType and lineItems may be null ("not found").
 */
public record ExtractedDocument(
        DocumentType documentType,
        String vendorName,
        String documentNumber,
        LocalDate documentDate,
        String currency,
        BigDecimal totalAmount,
        List<ExtractedLineItem> lineItems) {
}
