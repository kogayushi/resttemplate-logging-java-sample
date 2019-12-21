package resttemplatelogging.javaexample;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final RestTemplateProperties restTemplateProperties;

    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        this.logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        if (restTemplateProperties.shouldIncludePayload()) {
            BufferingClientHttpResponseWrapper wrappedResponse = new BufferingClientHttpResponseWrapper(response);
            this.logResponse(wrappedResponse);
            return wrappedResponse;
        }
        this.logResponse(response);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        Map<String, List<String>> maskedHeader = maskedHeaders(request.getHeaders());
        String responseBody = new String(body, StandardCharsets.UTF_8);
        log.info("[API:Request] Request=[{}:{}], Headers=[{}], Body=[{}]",
                 request.getMethod(),
                 request.getURI(),
                 maskedHeader,
                 responseBody);
    }

    private void logResponse(BufferingClientHttpResponseWrapper response) throws IOException {
        String responseBody = this.buildResponseBody(response);
        logResponse(response, responseBody);
    }

    private String buildResponseBody(ClientHttpResponse response) throws IOException {
        StringBuilder inputStringBuilder = new StringBuilder();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line = bufferedReader.readLine();
            while (line != null) {
                inputStringBuilder.append(line);
                inputStringBuilder.append('\n');
                line = bufferedReader.readLine();
            }
        } catch (Exception ex) {
            String msg = "Something went wrong during reading response body";
            log.error(msg, ex);
            throw ex;
        }
        return inputStringBuilder.toString();
    }

    private void logResponse(ClientHttpResponse response) throws IOException {
        String responseBody = "omitted response body";
        logResponse(response, responseBody);
    }

    private void logResponse(ClientHttpResponse response, String responseBody) throws IOException {
        Map<String, List<String>> maskedHeader = maskedHeaders(response.getHeaders());
        log.info("[API:Response] Status=[{}:{}], Headers=[{}], Body=[{}]",
                 response.getStatusCode().value(),
                 response.getStatusText(),
                 maskedHeader,
                 responseBody);
    }

    private Map<String, List<String>> maskedHeaders(HttpHeaders headers) {
        return headers.entrySet()
                      .stream()
                      .collect(toMap(Map.Entry::getKey, it -> maskedIfNeed(it.getKey(), it.getValue())));
    }

    private List<String> maskedIfNeed(String key, List<String> value) {
        if (shouldMask(key)) {
            int keepLength = restTemplateProperties.lengthRetainingOf(key);

            return value.stream()
                        .map(header -> masked(keepLength, header))
                        .collect(toList());
        }
        return value;
    }

    private boolean shouldMask(String keyword) {
        return restTemplateProperties.getMaskingKeywords()
                                     .stream()
                                     .anyMatch(masking -> masking.isSameWith(keyword));
    }

    private String masked(int keepLength, String header) {
        String maskedString = "<<***masked***>>";
        int lengthEnoughToBeMasked = keepLength * 2 + 1;
        if (header.length() > lengthEnoughToBeMasked) {
            return String.format("%s" + maskedString + "%s",
                                 header.substring(0, keepLength),
                                 header.substring(header.length() - keepLength));
        }
        return maskedString;
    }

    private static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {
        private byte[] body;
        private final ClientHttpResponse response;


        public HttpStatus getStatusCode() throws IOException {
            return this.response.getStatusCode();
        }

        public int getRawStatusCode() throws IOException {
            return this.response.getRawStatusCode();
        }

        public String getStatusText() throws IOException {
            return this.response.getStatusText();
        }

        public HttpHeaders getHeaders() {
            return this.response.getHeaders();
        }

        public InputStream getBody() throws IOException {
            if (this.body == null) {
                this.body = StreamUtils.copyToByteArray(this.response.getBody());
            }

            return new ByteArrayInputStream(this.body);
        }

        public void close() {
            this.response.close();
        }

        public BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
            this.response = response;
        }
    }
}
