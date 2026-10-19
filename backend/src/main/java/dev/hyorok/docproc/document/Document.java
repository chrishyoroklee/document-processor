package dev.hyorok.docproc.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import dev.hyorok.docproc.correction.Correction;

// An @Entity is a Java class that maps to a table. Each instance is one row.
// Entities can't be records: JPA needs a no-arg constructor and mutable fields
// so Hibernate can create the object first and fill it in afterwards.
@Entity
@Table(name = "documents")
public class Document {

    // IDENTITY = the database generates the id (our GENERATED ALWAYS AS IDENTITY column).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    // STRING stores "INVOICE", not the enum's position (0). Positions break silently
    // if someone reorders the enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.EXTRACTED;

    // Field names are camelCase; Spring's naming strategy maps vendorName -> vendor_name.
    private String vendorName;
    private String documentNumber;
    private LocalDate documentDate;

    // The column is CHAR(3), not VARCHAR. Without this, ddl-auto=validate fails at
    // startup with "wrong column type encountered in column [currency]".
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    private BigDecimal totalAmount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // One document has many line items.
    //   mappedBy      : the foreign key lives on LineItem.document, not here
    //   cascade ALL   : saving/deleting a Document also saves/deletes its items
    //   orphanRemoval : removing an item from this list deletes its row
    // The collection is LAZY by default: Hibernate only queries line_items when
    // something actually reads this list.
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LineItem> lineItems = new ArrayList<>();

    // Past corrections Claude said it used when extracting this document.
    // Many-to-many needs a join table: each row links one document to one correction.
    // No cascade: corrections exist on their own, linking must never create or delete them.
    // A Set rather than a List: Hibernate handles many-to-many Sets more efficiently,
    // and a correction can't be applied twice to the same document anyway.
    @ManyToMany
    @JoinTable(
            name = "document_applied_corrections",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "correction_id"))
    private Set<Correction> appliedCorrections = new HashSet<>();

    protected Document() {
        // for JPA
    }

    public Document(String fileName, DocumentType documentType) {
        this.fileName = fileName;
        this.documentType = documentType;
    }

    /**
     * Replaces all line items. Both sides of the relationship must be set:
     * the list here and the back reference on each item.
     */
    public void replaceLineItems(List<LineItem> items) {
        lineItems.clear();
        for (int i = 0; i < items.size(); i++) {
            LineItem item = items.get(i);
            item.attachTo(this, i);
            lineItems.add(item);
        }
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Correction> getAppliedCorrections() {
        return Collections.unmodifiableSet(appliedCorrections);
    }

    public void setAppliedCorrections(Collection<Correction> corrections) {
        appliedCorrections.clear();
        appliedCorrections.addAll(corrections);
    }

    /** Read-only view so callers must go through {@link #replaceLineItems}. */
    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }
}
