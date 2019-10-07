package se.ikama.bauta.batch.tasklet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Getter
public class ReportGenerationResult {
    public enum ReportGenerationResultStatus {
        OK,
        /* Indicates that the step should be considered failed. */
        Failed};

    @NonNull
    private ReportGenerationResultStatus status;
    private String message;

}
