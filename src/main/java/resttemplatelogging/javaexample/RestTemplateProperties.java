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
@Validated
@Component
@ConfigurationProperties(prefix = "resttemplate.logging")
public class RestTemplateProperties {

    @NotNull
    private Boolean shouldIncludePayload = false;

    @NotNull
    private List<MaskingKeyword> maskingKeywords = Collections.emptyList();

    public boolean shouldIncludePayload() {
        return this.shouldIncludePayload;
    }

    public int lengthRetainingOf(String keyword) {
        return this.maskingKeywords.stream()
                                   .filter(it -> it.isSameWith(keyword))
                                   .mapToInt(it -> it.lengthRetainingOriginalString)
                                   .findFirst().orElse(0);
    }

    @Data
    public static class MaskingKeyword {

        @NotNull
        private String keyword;

        @NotNull
        @Min(0)
        private Integer lengthRetainingOriginalString = 0;

        public boolean isSameWith(String keyword) {
            return this.keyword.equalsIgnoreCase(keyword);
        }
    }
}
