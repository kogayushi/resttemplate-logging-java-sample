package resttemplatelogging.javaexample;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@RestController
public class SampleController {
    private final RestTemplate restTemplate;

    @GetMapping("/")
    public String index() {
        return restTemplate.getForObject("http://weather.livedoor.com/forecast/webservice/json/v1?city=130010", String.class);
    }
}
