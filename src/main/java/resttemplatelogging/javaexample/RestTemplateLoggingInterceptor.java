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
        if (restTemplateProperties.shouldIncludePayload()) { // 設定でpayloadを出力するとなっていた場合のみ出力する
            BufferingClientHttpResponseWrapper wrappedResponse = new BufferingClientHttpResponseWrapper(response);
            this.logResponse(wrappedResponse);
            return wrappedResponse;
        }
        this.logResponse(response);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        Map<String, List<String>> maskedHeader = maskedHeaders(request.getHeaders()); // header情報の一部を秘匿する
        String responseBody = new String(body, StandardCharsets.UTF_8);
        log.info("[API:Request] Request=[{}:{}], Headers=[{}], Body=[{}]",
                 request.getMethod(),
                 request.getURI(),
                 maskedHeader,
                 responseBody);
    }

    // BufferingClientHttpResponseWrapperが渡された場合、payloadを出力すると判断する。分岐をオーバーロードで表現している。
    private void logResponse(BufferingClientHttpResponseWrapper response) throws IOException {
        String responseBody = this.buildResponseBody(response); // responseのpayloadを取得する
        logResponse(response, responseBody);
    }

    private String buildResponseBody(ClientHttpResponse response) throws IOException {
        StringBuilder inputStringBuilder = new StringBuilder();

        // 入寮ストリームを開くのでtry with resource文で確実にcloseする
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line = bufferedReader.readLine();
            while (line != null) {
                inputStringBuilder.append(line);
                line = bufferedReader.readLine();
                if (line != null) {
                    inputStringBuilder.append('\n');
                }
            }
        } catch (Exception ex) {
            String msg = "Something went wrong during reading response body";
            log.error(msg, ex);
            throw ex;
        }
        return inputStringBuilder.toString();
    }

    // ClientHttpResponseが渡された場合、payloadを出力しないと判断する。分岐をオーバーロードで表現している。
    private void logResponse(ClientHttpResponse response) throws IOException {
        String responseBody = "omitted response body"; // payloadを出力しないので代替テキストをハードコード
        logResponse(response, responseBody);
    }

    private void logResponse(ClientHttpResponse response, String responseBody) throws IOException {
        Map<String, List<String>> maskedHeader = maskedHeaders(response.getHeaders()); // headerを秘匿する
        log.info("[API:Response] Status=[{}:{}], Headers=[{}], Body=[{}]",
                 response.getStatusCode().value(),
                 response.getStatusText(),
                 maskedHeader,
                 responseBody);
    }

    private Map<String, List<String>> maskedHeaders(HttpHeaders headers) {
        return headers.entrySet()
                      .stream()
                      .collect(toMap(Map.Entry::getKey, it -> maskedIfNeed(it.getKey(), it.getValue()) /* 指定されたheaderのみ秘匿する */));
    }

    private List<String> maskedIfNeed(String headerName, List<String> headers) {
        // 秘匿が必要かどうか判断する
        if (shouldMask(headerName)) {
            // 何文字オリジナルの文字列を残すかを取得する
            int lengthRetained = restTemplateProperties.lengthRetainingOf(headerName);
            return headers.stream()
                          .map(header -> masked(header, lengthRetained)) // オリジナルの文字列を一部残しつつ、秘匿する
                          .collect(toList());
        }
        // 秘匿が不要ならオリジナルをそのまま返す
        return headers;
    }

    private boolean shouldMask(String headerName) {
        // 秘匿対象と設定されたheaderかどうかチェックする
        return restTemplateProperties.getMaskingHeaders()
                                     .stream()
                                     .anyMatch(headerNeededToBeMasked -> headerNeededToBeMasked.isSameWith(headerName));
    }

    private String masked(String header, int lengthRetained) {
        String maskedString = "<<***masked***>>"; // 秘匿する際の代替テキスト。秘匿されたとわかる表現になっていれば何でも良い。
        int lengthEnoughToBeMasked = lengthRetained * 2 + 1;
        if (header.length() > lengthEnoughToBeMasked) { // 秘匿時に残す文字数がオリジナルの文字数を超えるとオリジナルの文字列がそのまま出力されてしまうので、そうなっていないかチェックする
            return String.format("%s%s%s",
                                 header.substring(0, lengthRetained),
                                 maskedString,
                                 header.substring(header.length() - lengthRetained));
        }
        // 秘匿時に残す文字数がオリジナルの文字数を超えていた場合は、秘匿用の代替テキストをそのまま帰す
        return maskedString;
    }

    // org.springframework.http.client.BufferingClientHttpResponseWrapperをコピーしたもの。他の用途で使う想定はないので、privateにしている。
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
