package dev.hyorok.docproc.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.hyorok.docproc.TestcontainersConfiguration;
import dev.hyorok.docproc.correction.CorrectionRepository;
import dev.hyorok.docproc.extraction.ClaudeExtractor;
import dev.hyorok.docproc.extraction.ExtractedDocument;
import dev.hyorok.docproc.extraction.ExtractedLineItem;
import dev.hyorok.docproc.extraction.ExtractionException;
import dev.hyorok.docproc.extraction.PastCorrection;

// @SpringBootTest starts the whole application, the same as `java -jar`, but
// against the Testcontainers Postgres. @AutoConfigureMockMvc gives us MockMvc,
// which sends HTTP requests straight into Spring MVC without opening a port.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentControllerTest {

    static final String INVOICE_JSON = """
            {
              "documentType": "INVOICE",
              "vendorName": "Acme Corp",
              "documentNumber": "INV-001",
              "documentDate": "2026-10-01",
              "currency": "USD",
              "totalAmount": 150.50,
              "lineItems": [
                {"description": "Widget", "quantity": 2, "unitPrice": 50, "amount": 100},
                {"description": "Gadget", "quantity": 1, "unitPrice": 50.50, "amount": 50.50}
              ]
            }
            """;

    // Field injection is fine in tests (in production code, prefer constructors).
    @Autowired
    MockMvc mvc;

    @Autowired
    DocumentRepository repository;

    @Autowired
    CorrectionRepository correctionRepository;

    // Replaces the real ClaudeExtractor bean with a Mockito mock, so these tests
    // never call the Claude API, need no API key, and cost nothing.
    @MockitoBean
    ClaudeExtractor extractor;

    // Tests share one database, so start each one from empty.
    @BeforeEach
    void cleanDatabase() {
        // Documents first: deleting them also removes their applied-correction links.
        repository.deleteAll();
        correctionRepository.deleteAll();
    }

    @Test
    void createReturns201WithLineItemsInOrder() throws Exception {
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(INVOICE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fileName").value("manual"))
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.vendorName").value("Acme Corp"))
                .andExpect(jsonPath("$.totalAmount").value(150.50))
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andExpect(jsonPath("$.lineItems[0].description").value("Widget"))
                .andExpect(jsonPath("$.lineItems[1].position").value(1));
    }

    @Test
    void getReturnsWhatWasSaved() throws Exception {
        long id = createInvoice();

        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("INV-001"))
                .andExpect(jsonPath("$.documentDate").value("2026-10-01"))
                .andExpect(jsonPath("$.lineItems[1].description").value("Gadget"));
    }

    @Test
    void listReturnsSummariesWithoutLineItems() throws Exception {
        createInvoice();
        createInvoice();

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].vendorName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].lineItems").doesNotExist());
    }

    @Test
    void getUnknownIdReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/api/documents/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Document 999999 not found"));
    }

    @Test
    void deleteRemovesDocumentAndLineItems() throws Exception {
        long id = createInvoice();

        mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void updateReplacesFieldsAndLineItemsAndMarksReviewed() throws Exception {
        long id = uploadInvoice();
        String corrected = """
                {
                  "documentType": "RECEIPT",
                  "vendorName": "Acme Corporation",
                  "currency": "EUR",
                  "totalAmount": 99.99,
                  "lineItems": [{"description": "Only item", "amount": 99.99}]
                }
                """;

        mvc.perform(put("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON).content(corrected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.documentType").value("RECEIPT"))
                .andExpect(jsonPath("$.vendorName").value("Acme Corporation"))
                // Fields left out of a PUT are cleared: PUT replaces the whole document.
                .andExpect(jsonPath("$.documentNumber").doesNotExist())
                .andExpect(jsonPath("$.lineItems.length()").value(1))
                .andExpect(jsonPath("$.lineItems[0].id").isNumber());

        // The old line items are really gone from the database (orphanRemoval).
        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(jsonPath("$.lineItems.length()").value(1))
                .andExpect(jsonPath("$.lineItems[0].description").value("Only item"));
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        mvc.perform(put("/api/documents/{id}", 999_999).contentType(MediaType.APPLICATION_JSON).content(INVOICE_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        String invalid = """
                {
                  "currency": "us dollars",
                  "totalAmount": -5,
                  "lineItems": [{"description": ""}]
                }
                """;

        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.documentType").value("must not be null"))
                .andExpect(jsonPath("$.errors.currency").value("must be a 3-letter ISO code like USD"))
                .andExpect(jsonPath("$.errors.totalAmount").exists())
                .andExpect(jsonPath("$.errors['lineItems[0].description']").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"documentType\": \"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void uploadSavesWhatClaudeExtracted() throws Exception {
        var pdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF".getBytes());
        when(extractor.extract(any(), eq("application/pdf"), any())).thenReturn(new ExtractedDocument(
                DocumentType.INVOICE, "Acme Corp", "INV-9", LocalDate.of(2026, 10, 1), "USD",
                new BigDecimal("150.50"),
                List.of(new ExtractedLineItem("Widget", BigDecimal.TWO, new BigDecimal("50"), new BigDecimal("100")),
                        new ExtractedLineItem("Shipping", null, null, new BigDecimal("50.50"))),
                List.of()));

        mvc.perform(multipart("/api/documents/upload").file(pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("invoice.pdf"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.documentNumber").value("INV-9"))
                .andExpect(jsonPath("$.lineItems[0].description").value("Widget"))
                .andExpect(jsonPath("$.lineItems[1].description").value("Shipping"))
                .andExpect(jsonPath("$.lineItems[1].quantity").doesNotExist());
    }

    @Test
    void uploadRejectsUnsupportedFileTypeWithoutCallingClaude() throws Exception {
        var text = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/api/documents/upload").file(text))
                .andExpect(status().isUnsupportedMediaType());

        verify(extractor, never()).extract(any(), any(), any());
    }

    @Test
    void uploadReturns502WhenExtractionFails() throws Exception {
        var png = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});
        when(extractor.extract(any(), any(), any())).thenThrow(new ExtractionException("Claude API returned 500"));

        mvc.perform(multipart("/api/documents/upload").file(png))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Claude API returned 500"));
    }

    @Test
    void correctionsAreRememberedAndSentWithTheNextUpload() throws Exception {
        // 1. Claude extracts the vendor name slightly wrong.
        long first = uploadInvoice();

        // 2. A reviewer fixes it (and nothing else).
        String fixed = """
                {"documentType": "INVOICE", "vendorName": "Acme Corporation", "documentNumber": "INV-9",
                 "currency": "USD", "totalAmount": 150.50,
                 "lineItems": [{"description": "Widget", "amount": 100}, {"description": "Gadget", "amount": 50.50}]}
                """;
        mvc.perform(put("/api/documents/{id}", first).contentType(MediaType.APPLICATION_JSON).content(fixed))
                .andExpect(status().isOk());

        assertThat(correctionRepository.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getFieldName()).isEqualTo("vendorName");
            assertThat(c.getExtractedValue()).isEqualTo("Acme Corp");
            assertThat(c.getCorrectedValue()).isEqualTo("Acme Corporation");
            assertThat(c.getVendorName()).isEqualTo("Acme Corporation");
        });
        long correctionId = correctionRepository.findAll().getFirst().getId();

        // 3. Next upload: Claude says it applied the correction (and invents an id we never sent).
        when(extractor.extract(any(), any(), any())).thenReturn(new ExtractedDocument(
                DocumentType.INVOICE, "Acme Corporation", "INV-10", null, "USD", new BigDecimal("20"),
                List.of(), List.of(correctionId, 424242L)));
        var pdf = new MockMultipartFile("file", "invoice-2.pdf", "application/pdf", "%PDF".getBytes());

        mvc.perform(multipart("/api/documents/upload").file(pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorName").value("Acme Corporation"))
                // Only the real correction is kept; the invented id is dropped.
                .andExpect(jsonPath("$.appliedCorrections.length()").value(1))
                .andExpect(jsonPath("$.appliedCorrections[0].fieldName").value("vendorName"))
                .andExpect(jsonPath("$.appliedCorrections[0].correctedValue").value("Acme Corporation"));

        // The correction was actually handed to the extractor for the second upload.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PastCorrection>> sent = ArgumentCaptor.forClass(List.class);
        verify(extractor, times(2)).extract(any(), any(), sent.capture());
        assertThat(sent.getAllValues().get(0)).isEmpty();
        assertThat(sent.getAllValues().get(1)).singleElement()
                .extracting(PastCorrection::correctedValue).isEqualTo("Acme Corporation");
    }

    @Test
    void editingAnAlreadyReviewedDocumentRecordsNoCorrections() throws Exception {
        long id = createInvoice(); // manual entry, already REVIEWED
        String edited = INVOICE_JSON.replace("Acme Corp", "Someone Else");

        mvc.perform(put("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isOk());

        assertThat(correctionRepository.count()).isZero();
    }

    /** Creates a document the way the app normally does: upload + extraction. */
    private long uploadInvoice() throws Exception {
        when(extractor.extract(any(), any(), any())).thenReturn(new ExtractedDocument(
                DocumentType.INVOICE, "Acme Corp", "INV-9", null, "USD", new BigDecimal("150.50"),
                List.of(new ExtractedLineItem("Widget", null, null, new BigDecimal("100")),
                        new ExtractedLineItem("Gadget", null, null, new BigDecimal("50.50"))),
                List.of()));
        var pdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF".getBytes());
        String body = mvc.perform(multipart("/api/documents/upload").file(pdf))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private long createInvoice() throws Exception {
        String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(INVOICE_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
