package dev.hyorok.docproc.document;

import org.springframework.data.jpa.repository.JpaRepository;

// No implementation needed: Spring Data generates one at startup.
// JpaRepository<Document, Long> gives us save, findById, findAll, deleteById, existsById, ...
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
