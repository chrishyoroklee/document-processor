package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record LineItemRequest(
        @NotBlank String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {
}
