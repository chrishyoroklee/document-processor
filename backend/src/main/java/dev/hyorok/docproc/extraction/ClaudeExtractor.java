package dev.hyorok.docproc.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a document to the Claude Messages API and turns the reply into an
 * {@link ExtractedDocument}. Knows nothing about the database.
 *
 * <p>Uses structured outputs: {@code output_config.format} carries a JSON Schema,
 * and the API guarantees the reply text is JSON matching it.
 */
@Component
public class ClaudeExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClaudeExtractor.class);

    private static final String SYSTEM_PROMPT = """
            You extract data from business documents (invoices, receipts, purchase orders).
            Use null for any field that is not present in the document. Never guess.
            Copy numbers exactly as printed, without currency symbols.""";

    private static final String CORRECTIONS_INTRO = """


            Reviewers corrected some earlier extractions. Each line is:
            [id] vendor | field | what was extracted -> what it should have been

            If the document you are reading is from the same vendor and the same mistake
            would happen again, use the corrected form and put that line's id in
            appliedCorrectionIds. Ignore corrections for other vendors, and don't apply a
            correction just because the field name matches. If none apply, return an
            empty appliedCorrectionIds list.

            """;

    private final RestClient restClient;
    private final ClaudeProperties properties;
    private final JsonMapper jsonMapper;
    private final JsonNode schema;

    // RestClient.Builder is provided by Spring Boot, already configured with
    // Jackson and the timeouts from spring.http.clients.* in application.yml.
    public ClaudeExtractor(RestClient.Builder builder, ClaudeProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.schema = loadSchema(jsonMapper);
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-api-key", properties.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                // Opts in to server-side fallback: if a safety filter wrongly declines
                // a document, the API retries on another model within the same call.
                .defaultHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .build();
    }

    /**
     * @param corrections recent reviewer fixes, sent along so Claude can avoid
     *                    repeating mistakes; may be empty
     */
    public ExtractedDocument extract(byte[] content, String mediaType, List<PastCorrection> corrections) {
        Map<String, Object> request = buildRequest(content, mediaType, corrections);

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 4xx/5xx from the API. The body explains what went wrong; log it for us,
            // but don't pass API details on to our own callers.
            log.warn("Claude API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExtractionException("Claude API returned " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // Network problems, timeouts, ...
            throw new ExtractionException("Could not reach the Claude API", e);
        }

        return parseResponse(responseBody);
    }

    /**
     * Builds the JSON request body as nested Maps and Lists. Jackson serializes it
     * exactly as written, so this reads almost like the JSON in the API docs.
     */
    Map<String, Object> buildRequest(byte[] content, String mediaType, List<PastCorrection> corrections) {
        // PDFs go in a "document" block, images in an "image" block. Same source shape.
        String blockType = MediaType.APPLICATION_PDF_VALUE.equals(mediaType) ? "document" : "image";
        Map<String, Object> fileBlock = Map.of(
                "type", blockType,
                "source", Map.of(
                        "type", "base64",
                        "media_type", mediaType,
                        // The basic encoder: no line breaks, which the API requires.
                        "data", Base64.getEncoder().encodeToString(content)));

        return Map.of(
                "model", properties.model(),
                "max_tokens", 16000,
                "system", systemPrompt(corrections),
                "output_config", Map.of(
                        "effort", properties.effort(),
                        "format", Map.of("type", "json_schema", "schema", schema)),
                "fallbacks", "default",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                fileBlock,
                                Map.of("type", "text", "text", "Extract the fields from this document.")))));
    }

    /** The fixed instructions, plus one line per past correction when there are any. */
    static String systemPrompt(List<PastCorrection> corrections) {
        if (corrections.isEmpty()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + CORRECTIONS_INTRO + corrections.stream()
                .map(c -> "[%d] %s | %s | %s -> %s".formatted(
                        c.id(), c.vendorName(), c.fieldName(), quote(c.extractedValue()), quote(c.correctedValue())))
                .collect(Collectors.joining("\n"));
    }

    private static String quote(String value) {
        return value == null ? "null" : '"' + value + '"';
    }

    /**
     * Pulls the JSON answer out of a Messages API response.
     * Package-private so the unit test can feed it saved responses.
     */
    ExtractedDocument parseResponse(String responseBody) {
        try {
            JsonNode response = jsonMapper.readTree(responseBody);

            // Always check stop_reason before trusting the content.
            String stopReason = response.path("stop_reason").asString();
            if ("refusal".equals(stopReason)) {
                throw new ExtractionException("Claude declined to process this document");
            }
            if ("max_tokens".equals(stopReason)) {
                throw new ExtractionException("Claude's answer was cut off (max_tokens)");
            }

            // The content array can hold thinking blocks before the answer.
            // The answer is the first block of type "text".
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asString())) {
                    return jsonMapper.readValue(block.path("text").asString(), ExtractedDocument.class);
                }
            }
            throw new ExtractionException("Claude's response had no text block");
        } catch (JacksonException e) {
            throw new ExtractionException("Could not parse Claude's response", e);
        }
    }

    private static JsonNode loadSchema(JsonMapper jsonMapper) {
        // src/main/resources is on the classpath, so the file is found by name.
        try (InputStream in = new ClassPathResource("extraction-schema.json").getInputStream()) {
            return jsonMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("extraction-schema.json is missing", e);
        }
    }
}
