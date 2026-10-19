package dev.hyorok.docproc.extraction;

/**
 * A reviewer's earlier fix, as the extractor sees it. A plain record rather than
 * the Correction entity, so this package still knows nothing about the database.
 */
public record PastCorrection(
        long id,
        String vendorName,
        String fieldName,
        String extractedValue,
        String correctedValue) {
}
