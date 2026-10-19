package dev.hyorok.docproc.document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.hyorok.docproc.correction.FieldChange;
import dev.hyorok.docproc.document.dto.DocumentRequest;

/** Works out which header fields a reviewer changed. Pure logic, no database. */
final class FieldChanges {

    private FieldChanges() {
    }

    static List<FieldChange> between(Document before, DocumentRequest after) {
        List<FieldChange> changes = new ArrayList<>();
        compare(changes, "documentType", before.getDocumentType(), after.documentType());
        compare(changes, "vendorName", before.getVendorName(), after.vendorName());
        compare(changes, "documentNumber", before.getDocumentNumber(), after.documentNumber());
        compare(changes, "documentDate", before.getDocumentDate(), after.documentDate());
        compare(changes, "currency", before.getCurrency(), after.currency());
        compareAmounts(changes, "totalAmount", before.getTotalAmount(), after.totalAmount());
        return changes;
    }

    private static void compare(List<FieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, text(before), text(after)));
        }
    }

    // BigDecimal.equals also compares scale, so 150.5 and 150.50 would count as
    // "different". compareTo compares only the value.
    private static void compareAmounts(List<FieldChange> changes, String field, BigDecimal before, BigDecimal after) {
        boolean same = before == null ? after == null : after != null && before.compareTo(after) == 0;
        if (!same) {
            changes.add(new FieldChange(field, text(before), text(after)));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        // toPlainString avoids scientific notation like 1E+3.
        return value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
    }
}
