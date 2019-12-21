package resttemplatelogging.javaexample;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@Data
@Validated // 正しく設定されていない場合はアプリを起動させたくないので、`@Validated`を付与する
@Component
@ConfigurationProperties(prefix = "resttemplate.logging")
public class RestTemplateProperties {

    private boolean shouldIncludePayload = false; // requestとresponseのpayloadのログ出力有無を設定可能にする。

    private List<MaskingHeader> maskingHeaders = Collections.emptyList();

    // lombokが生やすboolean用のgetterは可読性が悪いので自分で定義する
    public boolean shouldIncludePayload() {
        return this.shouldIncludePayload;
    }

    // 設定用のクラスかもしれないが、クライアントコードから使いやすくなるようにヘルパーメソッドを生やす。
    public int lengthRetainingOf(String keyword) {
        return this.maskingHeaders.stream()
                                  .filter(it -> it.isSameWith(keyword))
                                  .mapToInt(it -> it.lengthRetainingOriginalString)
                                  .findFirst().orElse(0);
    }

    @Data
    public static class MaskingHeader {

        @NotNull
        private String name; // 秘匿対象とするheader名

        @NotNull
        @Min(0)
        private Integer lengthRetainingOriginalString = 0;

        // 設定用のクラスかもしれないが、クライアントコードから使いやすくなるようにヘルパーメソッドを生やす。
        public boolean isSameWith(String headerName) {
            return this.name.equalsIgnoreCase(headerName);
        }
    }
}
