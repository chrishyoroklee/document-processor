package dev.hyorok.docproc.web;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.hyorok.docproc.document.DocumentNotFoundException;
import dev.hyorok.docproc.document.UnsupportedFileTypeException;
import dev.hyorok.docproc.extraction.ExtractionException;

// @RestControllerAdvice applies to every controller. When a controller (or the
// service it calls) throws, Spring looks here for a matching @ExceptionHandler
// and returns whatever it produces. Controllers never need try/catch.
//
// ProblemDetail is the standard JSON error shape (RFC 9457):
//   {"type": "about:blank", "title": "Not Found", "status": 404, "detail": "..."}
//
// Extending ResponseEntityExceptionHandler adds ProblemDetail responses for all of
// Spring MVC's own exceptions too: malformed JSON (400), validation (400), file too
// large (413), wrong HTTP method (405), and so on.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ProblemDetail handleUnsupportedFileType(UnsupportedFileTypeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    // 502 Bad Gateway: we're fine, but the service we depend on (Claude) failed us.
    @ExceptionHandler(ExtractionException.class)
    public ProblemDetail handleExtractionFailure(ExtractionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /**
     * The default 400 for a failed @Valid only says "Invalid request content".
     * Add which fields failed and why, so the frontend can show it:
     * {@code "errors": {"currency": "must be a 3-letter ISO code like USD"}}
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
