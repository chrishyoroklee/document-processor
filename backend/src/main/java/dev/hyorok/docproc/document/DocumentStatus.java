package dev.hyorok.docproc.document;

public enum DocumentStatus {
    /** Created from an LLM extraction; nobody has checked it yet. */
    EXTRACTED,
    /** A person has checked or corrected it. */
    REVIEWED
}
