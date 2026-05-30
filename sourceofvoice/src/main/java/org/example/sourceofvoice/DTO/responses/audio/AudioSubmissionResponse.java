package org.example.sourceofvoice.DTO.responses.audio;

import lombok.Data;
import org.example.sourceofvoice.entities.audio.AudioSubmissionStatus;

import java.math.BigDecimal;

@Data
public class AudioSubmissionResponse {
    private Long id;
    private Long audioTextId;
    private AudioSubmissionStatus status;
    private Double correctnessScore;
    private BigDecimal payoutAmount;
}
