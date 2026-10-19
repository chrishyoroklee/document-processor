package dev.hyorok.docproc.document.dto;

import java.math.BigDecimal;

import dev.hyorok.docproc.document.LineItem;

public record LineItemResponse(
        Long id,
        int position,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public static LineItemResponse from(LineItem item) {
        return new LineItemResponse(
                item.getId(),
                item.getPosition(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAmount());
    }
}
