package dev.hyorok.docproc.document;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hyorok.docproc.document.dto.DocumentRequest;
import dev.hyorok.docproc.document.dto.DocumentResponse;
import dev.hyorok.docproc.document.dto.DocumentSummary;

// @Service marks this as a Spring bean (an object Spring creates and manages).
// Business logic lives here. Entities go in and out of the repository here, and
// only DTOs leave this class.
@Service
public class DocumentService {

    private final DocumentRepository repository;

    // Constructor injection: Spring sees the parameter type, finds the matching
    // bean (the generated DocumentRepository), and passes it in. No @Autowired needed
    // when there is a single constructor.
    public DocumentService(DocumentRepository repository) {
        this.repository = repository;
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
