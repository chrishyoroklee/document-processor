package dev.hyorok.docproc.document;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long id) {
        super("Document " + id + " not found");
    }
}
