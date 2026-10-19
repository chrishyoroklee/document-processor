package dev.hyorok.docproc.document;

public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String contentType) {
        super("Unsupported file type: " + contentType + ". Upload a PDF, PNG, JPEG or WEBP file.");
    }
}
