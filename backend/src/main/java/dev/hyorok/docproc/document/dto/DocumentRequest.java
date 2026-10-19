package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import dev.hyorok.docproc.document.DocumentType;

// A record is an immutable data class: Java generates the constructor, accessors
// (documentType(), not getDocumentType()), equals, hashCode and toString.
// Jackson maps JSON fields onto the record's components by name.
//
// The constraint annotations are checked when the controller parameter is marked
// @Valid. Every violation is collected and returned as one 400 response.
// Size limits match the column sizes in V1__create_documents.sql.
public record DocumentRequest(
        @NotNull DocumentType documentType,
        @Size(max = 255) String vendorName,
        @Size(max = 100) String documentNumber,
        LocalDate documentDate,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO code like USD") String currency,
        @PositiveOrZero BigDecimal totalAmount,
        // @Valid here cascades validation into each line item.
        @Valid List<LineItemRequest> lineItems) {

    // A "compact constructor" runs before the fields are assigned.
    // Here it turns a missing lineItems field into an empty list.
    public DocumentRequest {
        lineItems = lineItems == null ? List.of() : lineItems;
    }
}
