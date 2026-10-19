package dev.hyorok.docproc.document;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.hyorok.docproc.document.dto.DocumentRequest;
import dev.hyorok.docproc.document.dto.DocumentResponse;
import dev.hyorok.docproc.document.dto.DocumentSummary;

// The controller only translates HTTP <-> Java. No business logic, no repository.
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    // @RequestBody: Jackson parses the JSON body into a DocumentRequest.
    // ResponseEntity lets us pick the status (201) and add a Location header
    // pointing at the new resource.
    @PostMapping
    public ResponseEntity<DocumentResponse> create(@RequestBody DocumentRequest request) {
        DocumentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/documents/" + created.id())).body(created);
    }

    // multipart/form-data is how browsers upload files. @RequestParam("file") picks
    // the form field named "file".
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        DocumentResponse created = service.upload(file);
        return ResponseEntity.created(URI.create("/api/documents/" + created.id())).body(created);
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return service.list();
    }

    // @PathVariable binds {id} from the URL to the parameter.
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
