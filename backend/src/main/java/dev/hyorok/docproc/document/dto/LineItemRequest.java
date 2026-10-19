package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;

public record LineItemRequest(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {
}
