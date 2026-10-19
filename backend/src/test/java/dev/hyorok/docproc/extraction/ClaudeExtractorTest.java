package dev.hyorok.docproc.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import dev.hyorok.docproc.document.DocumentType;

// A plain unit test: no Spring context, no database, no real HTTP.
// MockRestServiceServer intercepts the RestClient's requests and returns canned responses.
class ClaudeExtractorTest {

    static final ClaudeProperties PROPERTIES =
            new ClaudeProperties("test-key", "https://api.example.test", "claude-opus-5-5", "low");

    MockRestServiceServer server;
    ClaudeExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        extractor = new ClaudeExtractor(builder, PROPERTIES, JsonMapper.builder().build());
    }

    @Test
    void sendsPdfAsDocumentBlockAndParsesTheAnswer() throws IOException {
        server.expect(requestTo("https://api.example.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.output_config.effort").value("low"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("document"))
                .andExpect(jsonPath("$.messages[0].content[0].source.media_type").value("application/pdf"))
                // "%PDF" base64-encoded
                .andExpect(jsonPath("$.messages[0].content[0].source.data").value("JVBERg=="))
                .andRespond(withSuccess(fixture("success.json"), MediaType.APPLICATION_JSON));

        ExtractedDocument result = extractor.extract("%PDF".getBytes(StandardCharsets.US_ASCII), "application/pdf");

        server.verify();
        assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
        assertThat(result.vendorName()).isEqualTo("Acme Corp");
        assertThat(result.documentDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        // BigDecimal: compare with isEqualByComparingTo, since 150.5 and 150.50 aren't equals()
        assertThat(result.totalAmount()).isEqualByComparingTo("150.50");
        assertThat(result.lineItems()).hasSize(2);
        assertThat(result.lineItems().get(1).quantity()).isNull();
    }

    @Test
    void sendsImagesAsImageBlock() {
        Map<String, Object> request = extractor.buildRequest(new byte[] {1, 2, 3}, "image/png");

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) ((List<?>) request.get("messages")).get(0);
        @SuppressWarnings("unchecked")
        var fileBlock = (Map<String, Object>) ((List<?>) message.get("content")).get(0);

        assertThat(fileBlock).containsEntry("type", "image");
    }

    @Test
    void refusalBecomesExtractionException() throws IOException {
        assertThatThrownBy(() -> extractor.parseResponse(fixture("refusal.json")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void textThatIsNotJsonBecomesExtractionException() throws IOException {
        assertThatThrownBy(() -> extractor.parseResponse(fixture("not-json.json")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("parse");
    }

    @Test
    void apiErrorBecomesExtractionException() {
        server.expect(requestTo("https://api.example.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}"));

        assertThatThrownBy(() -> extractor.extract(new byte[] {1}, "image/png"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("401");
    }

    @Test
    void parsedLineItemAmountsKeepTheirPrecision() throws IOException {
        ExtractedDocument result = extractor.parseResponse(fixture("success.json"));

        assertThat(result.lineItems().get(1).amount()).isEqualTo(new BigDecimal("50.50"));
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("claude/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
