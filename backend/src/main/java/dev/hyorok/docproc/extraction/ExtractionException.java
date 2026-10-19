package dev.hyorok.docproc.extraction;

/** Claude couldn't give us usable data. Mapped to HTTP 502 Bad Gateway. */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
