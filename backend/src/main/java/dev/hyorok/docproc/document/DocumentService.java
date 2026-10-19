package dev.hyorok.docproc.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.hyorok.docproc.document.dto.DocumentRequest;
import dev.hyorok.docproc.document.dto.DocumentResponse;
import dev.hyorok.docproc.document.dto.DocumentSummary;
import dev.hyorok.docproc.extraction.ClaudeExtractor;
import dev.hyorok.docproc.extraction.ExtractedDocument;

// @Service marks this as a Spring bean (an object Spring creates and manages).
// Business logic lives here. Entities go in and out of the repository here, and
// only DTOs leave this class.
@Service
public class DocumentService {

    // The file types Claude can read directly.
    private static final Set<String> SUPPORTED_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");

    private final DocumentRepository repository;
    private final ClaudeExtractor extractor;

    // Constructor injection: Spring sees the parameter types, finds the matching
    // beans, and passes them in. No @Autowired needed when there is a single constructor.
    public DocumentService(DocumentRepository repository, ClaudeExtractor extractor) {
        this.repository = repository;
        this.extractor = extractor;
    }

    // Deliberately NOT @Transactional. The Claude call can take many seconds, and a
    // transaction would hold a database connection the whole time. Instead we call
    // Claude first, then save() runs in its own short transaction.
    public DocumentResponse upload(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {
            throw new UnsupportedFileTypeException(contentType);
        }

        ExtractedDocument extracted = extractor.extract(readBytes(file), contentType);

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "upload");
        Document doc = new Document(fileName, extracted.documentType());
        doc.setVendorName(extracted.vendorName());
        doc.setDocumentNumber(extracted.documentNumber());
        doc.setDocumentDate(extracted.documentDate());
        doc.setCurrency(extracted.currency());
        doc.setTotalAmount(extracted.totalAmount());
        doc.replaceLineItems(extracted.lineItems().stream()
                .map(li -> new LineItem(li.description(), li.quantity(), li.unitPrice(), li.amount()))
                .toList());

        // The entity is new and its line items are a plain list in memory, so
        // building the DTO here needs no open transaction.
        return DocumentResponse.from(repository.save(doc));
    }

    // @Transactional opens a DB transaction for the whole method: either every
    // change is committed at the end, or (if an exception escapes) none are.
    @Transactional
    public DocumentResponse create(DocumentRequest request) {
        Document doc = new Document("manual", request.documentType());
        apply(request, doc);
        // A person typed this in, so it doesn't need reviewing.
        doc.setStatus(DocumentStatus.REVIEWED);
        return DocumentResponse.from(repository.save(doc));
    }

    // readOnly = true lets Hibernate skip dirty checking: a small speedup, and it
    // documents that this method doesn't change anything.
    @Transactional(readOnly = true)
    public List<DocumentSummary> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(DocumentSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id) {
        // DocumentResponse.from reads the lazy line items. That works because
        // we're still inside the transaction.
        return DocumentResponse.from(find(id));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            // A checked exception we can't do anything useful with: wrap it.
            throw new UncheckedIOException(e);
        }
    }

    private Document find(Long id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** Copies every editable field from the request onto the entity. */
    private static void apply(DocumentRequest request, Document doc) {
        doc.setDocumentType(request.documentType());
        doc.setVendorName(request.vendorName());
        doc.setDocumentNumber(request.documentNumber());
        doc.setDocumentDate(request.documentDate());
        doc.setCurrency(request.currency());
        doc.setTotalAmount(request.totalAmount());
        doc.replaceLineItems(request.lineItems().stream()
                .map(li -> new LineItem(li.description(), li.quantity(), li.unitPrice(), li.amount()))
                .toList());
    }
}
