package dev.hyorok.docproc.document.dto;

import dev.hyorok.docproc.correction.Correction;

/** A past correction that was used when extracting this document. */
public record AppliedCorrectionResponse(
        Long id,
        String vendorName,
        String fieldName,
        String extractedValue,
        String correctedValue) {

    public static AppliedCorrectionResponse from(Correction c) {
        return new AppliedCorrectionResponse(
                c.getId(), c.getVendorName(), c.getFieldName(), c.getExtractedValue(), c.getCorrectedValue());
    }
}
