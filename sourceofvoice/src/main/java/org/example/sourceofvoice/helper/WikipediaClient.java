package org.example.sourceofvoice.helper;

import lombok.RequiredArgsConstructor;
import org.example.sourceofvoice.DTO.responses.text.WikipediaExtractResponse;
import org.example.sourceofvoice.DTO.responses.text.WikipediaRandomResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WikipediaClient {

    private final WebClient.Builder clientBuilder;

    public Mono<WikipediaRandomResponse> getRandomPages(String languageCode, int limit){
        return buildClient(languageCode)
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("action", "query")
                        .queryParam("format", "json")
                        .queryParam("list", "random")
                        .queryParam("rnnamespace", "0")
                        .queryParam("rnlimit", limit)
                        .build())
                .retrieve()
                .bodyToMono(WikipediaRandomResponse.class)
                .timeout(Duration.ofSeconds(5));
    }

    public Mono<WikipediaExtractResponse> getExtracts(String languageCode, List<Long> pageIds, boolean introOnly) {
        String joinedPageIds = pageIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));

        return buildClient(languageCode)
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("prop", "extracts")
                            .queryParam("explaintext", "true")
                            .queryParam("pageids", joinedPageIds);
                    if (introOnly) {
                        builder.queryParam("exintro", "true");
                    }

                    return builder.build();
                })
                .retrieve()
                .bodyToMono(WikipediaExtractResponse.class)
                .timeout(Duration.ofSeconds(5));
    }

    private WebClient buildClient(String languageCode) {
        return clientBuilder
                .baseUrl("https://" + languageCode + ".wikipedia.org/w/api.php")
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "SourceOfVoiceUniversityProject/1.0"
                )
                .build();
    }

}
