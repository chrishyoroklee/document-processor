package dev.hyorok.docproc.extraction;

import java.math.BigDecimal;

public record ExtractedLineItem(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {
}
