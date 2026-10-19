package dev.hyorok.docproc.correction;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionService {

    private final CorrectionRepository repository;

    public CorrectionService(CorrectionRepository repository) {
        this.repository = repository;
    }

    // Called from DocumentService.update, which is already @Transactional. The default
    // propagation (REQUIRED) joins that transaction instead of starting a new one, so
    // the document update and its corrections are committed together or not at all.
    @Transactional
    public void record(Long documentId, String vendorName, List<FieldChange> changes) {
        repository.saveAll(changes.stream()
                .map(change -> new Correction(documentId, vendorName, change))
                .toList());
    }

    /** The corrections sent to Claude with every upload. Newest first. */
    @Transactional(readOnly = true)
    public List<Correction> recent() {
        return repository.findTop30ByOrderByCreatedAtDesc();
    }
}
