package resttemplatelogging.javaexample;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@Component
public class CustomRestTemplateCustomizer implements RestTemplateCustomizer {
    private final RestTemplateLoggingInterceptor restTemplateLoggingInterceptor;

    @Override
    public void customize(RestTemplate restTemplate) {
        restTemplate.getInterceptors().add(restTemplateLoggingInterceptor);
    }
}
