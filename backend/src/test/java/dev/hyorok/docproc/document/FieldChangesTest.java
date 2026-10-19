package dev.hyorok.docproc.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.hyorok.docproc.correction.FieldChange;
import dev.hyorok.docproc.document.dto.DocumentRequest;

class FieldChangesTest {

    private static Document extracted() {
        Document doc = new Document("invoice.pdf", DocumentType.INVOICE);
        doc.setVendorName("Northwind Supplies");
        doc.setDocumentNumber("NW-2026-0457");
        doc.setDocumentDate(LocalDate.of(2026, 10, 3));
        doc.setCurrency("USD");
        doc.setTotalAmount(new BigDecimal("162.00"));
        return doc;
    }

    private static DocumentRequest request(String vendor, LocalDate date, BigDecimal total) {
        return new DocumentRequest(DocumentType.INVOICE, vendor, "NW-2026-0457", date, "USD", total, List.of());
    }

    @Test
    void unchangedDocumentHasNoChanges() {
        var same = request("Northwind Supplies", LocalDate.of(2026, 10, 3), new BigDecimal("162.00"));

        assertThat(FieldChanges.between(extracted(), same)).isEmpty();
    }

    @Test
    void sameAmountWithDifferentScaleIsNotAChange() {
        var same = request("Northwind Supplies", LocalDate.of(2026, 10, 3), new BigDecimal("162"));

        assertThat(FieldChanges.between(extracted(), same)).isEmpty();
    }

    @Test
    void reportsEachChangedFieldAsText() {
        var corrected = request("Northwind Supplies Ltd.", LocalDate.of(2026, 10, 4), new BigDecimal("150.00"));

        assertThat(FieldChanges.between(extracted(), corrected)).containsExactly(
                new FieldChange("vendorName", "Northwind Supplies", "Northwind Supplies Ltd."),
                new FieldChange("documentDate", "2026-10-03", "2026-10-04"),
                new FieldChange("totalAmount", "162.00", "150.00"));
    }

    @Test
    void fillingInAMissingFieldIsAChange() {
        Document doc = extracted();
        doc.setVendorName(null);
        var corrected = request("Northwind Supplies", LocalDate.of(2026, 10, 3), new BigDecimal("162.00"));

        assertThat(FieldChanges.between(doc, corrected))
                .containsExactly(new FieldChange("vendorName", null, "Northwind Supplies"));
    }
}
