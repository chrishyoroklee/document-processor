package dev.hyorok.docproc.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import dev.hyorok.docproc.extraction.ClaudeExtractor;
import dev.hyorok.docproc.extraction.ExtractedDocument;
import dev.hyorok.docproc.extraction.ExtractedLineItem;
import dev.hyorok.docproc.extraction.ExtractionException;

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

    // Replaces the real ClaudeExtractor bean with a Mockito mock, so these tests
    // never call the Claude API, need no API key, and cost nothing.
    @MockitoBean
    ClaudeExtractor extractor;

    // Tests share one database, so start each one from empty.
    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
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
    void uploadSavesWhatClaudeExtracted() throws Exception {
        var pdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF".getBytes());
        when(extractor.extract(any(), eq("application/pdf"))).thenReturn(new ExtractedDocument(
                DocumentType.INVOICE, "Acme Corp", "INV-9", LocalDate.of(2026, 10, 1), "USD",
                new BigDecimal("150.50"),
                List.of(new ExtractedLineItem("Widget", BigDecimal.TWO, new BigDecimal("50"), new BigDecimal("100")),
                        new ExtractedLineItem("Shipping", null, null, new BigDecimal("50.50")))));

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

        verify(extractor, never()).extract(any(), any());
    }

    @Test
    void uploadReturns502WhenExtractionFails() throws Exception {
        var png = new MockMultipartFile("file", "receipt.png", "image/png", new byte[] {1, 2, 3});
        when(extractor.extract(any(), any())).thenThrow(new ExtractionException("Claude API returned 500"));

        mvc.perform(multipart("/api/documents/upload").file(png))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Claude API returned 500"));
    }

    private long createInvoice() throws Exception {
        String body = mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(INVOICE_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }
}
