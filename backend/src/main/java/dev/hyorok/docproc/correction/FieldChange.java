package dev.hyorok.docproc.correction;

/** One field a reviewer changed: what Claude extracted and what it should have been. */
public record FieldChange(String fieldName, String extractedValue, String correctedValue) {
}
