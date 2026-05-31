package org.example.sourceofvoice.helper;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class SpeechmanticsClient {

    private final WebClient webClient;

    public SpeechmanticsClient(
            WebClient.Builder builder,
            @Value("${speechmatics.base-url}") String baseUrl,
            @Value("${speechmatics.api-key}") String apiKey
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public Mono<CreateJobResponse> createTranscriptionJob(byte[] audioBytes, String filename, String languageCode){
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        String configJson = """
                {
                  "type": "transcription",
                  "transcription_config": {
                    "language": "%s"
                  }
                }
                """.formatted(languageCode);
        bodyBuilder.part("config", configJson).contentType(MediaType.APPLICATION_JSON);

        bodyBuilder.part("data_file" , new ByteArrayResource(audioBytes){
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                })
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return webClient.post()
                .uri("/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .retrieve()
                .bodyToMono(CreateJobResponse.class)
                .timeout(Duration.ofSeconds(15));
    }

    public Mono<JobDetailsResponse> getJobDetails(String jobId){
        return webClient.get()
                .uri("/jobs/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JobDetailsResponse.class)
                .timeout(Duration.ofSeconds(15));
    }

    public Mono<String> getTranscriptText(String jobId){
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/jobs/{jobId}/transcript")
                        .queryParam("format", "txt")
                        .build(jobId))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15));
    }

    @Data
    public static class CreateJobResponse {
        private String id;
    }

    @Data
    public static class JobDetailsResponse {
        private Job job;

        @Data
        public static class Job {
            private String id;
            private String status;
        }
    }


}
