package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.hyorok.docproc.document.DocumentType;

// A record is an immutable data class: Java generates the constructor, accessors
// (documentType(), not getDocumentType()), equals, hashCode and toString.
// Jackson maps JSON fields onto the record's components by name.
public record DocumentRequest(
        DocumentType documentType,
        String vendorName,
        String documentNumber,
        LocalDate documentDate,
        String currency,
        BigDecimal totalAmount,
        List<LineItemRequest> lineItems) {

    // A "compact constructor" runs before the fields are assigned.
    // Here it turns a missing lineItems field into an empty list.
    public DocumentRequest {
        lineItems = lineItems == null ? List.of() : lineItems;
    }
}
