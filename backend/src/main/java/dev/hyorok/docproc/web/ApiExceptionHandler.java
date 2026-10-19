package dev.hyorok.docproc.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.hyorok.docproc.document.DocumentNotFoundException;

// @RestControllerAdvice applies to every controller. When a controller (or the
// service it calls) throws, Spring looks here for a matching @ExceptionHandler
// and returns whatever it produces. Controllers never need try/catch.
//
// ProblemDetail is the standard JSON error shape (RFC 9457):
//   {"type": "about:blank", "title": "Not Found", "status": 404, "detail": "..."}
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
