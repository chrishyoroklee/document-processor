package dev.hyorok.docproc.correction;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

// documentId is a plain column, not a @ManyToOne to Document. That keeps this
// package independent: document depends on correction, never the other way round.
@Entity
@Table(name = "corrections")
public class Correction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long documentId;

    private String vendorName;

    @Column(nullable = false)
    private String fieldName;

    @Column(columnDefinition = "text")
    private String extractedValue;

    @Column(columnDefinition = "text")
    private String correctedValue;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Correction() {
        // for JPA
    }

    public Correction(Long documentId, String vendorName, FieldChange change) {
        this.documentId = documentId;
        this.vendorName = vendorName;
        this.fieldName = change.fieldName();
        this.extractedValue = change.extractedValue();
        this.correctedValue = change.correctedValue();
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getExtractedValue() {
        return extractedValue;
    }

    public String getCorrectedValue() {
        return correctedValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
