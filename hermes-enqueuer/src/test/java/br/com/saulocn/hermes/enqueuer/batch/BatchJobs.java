package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.arc.Arc;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Starts a batch job the way the {@code @Scheduled} triggers do, and blocks until it finishes.
 *
 * <p>Through {@link JobLauncher}, not {@code JobOperator.start} — going straight to JBeret was
 * one more way into the runtime that the overlap guard knew nothing about, in the one place that
 * claims to reproduce how the job really starts.
 */
final class BatchJobs {

    private BatchJobs() {
    }

    static void runToCompletion(JobLauncher.Job job) {
        BatchStatus status = runToTerminalStatus(job);
        assertEquals(BatchStatus.COMPLETED, status, "job " + job + " ended with status " + status);
    }

    /**
     * Runs the job and returns whatever terminal status it reaches. Use this when the failure
     * path is the subject of the test — waiting for COMPLETED would just burn the timeout.
     */
    static BatchStatus runToTerminalStatus(JobLauncher.Job job) {
        long executionId = Arc.container().instance(JobLauncher.class).get()
                .startIfIdle(job)
                .orElseThrow(() -> new IllegalStateException(
                        "job " + job + " was already running when the test tried to start it"));

        JobOperator jobOperator = BatchRuntime.getJobOperator();

        await().atMost(Duration.ofSeconds(60)).until(
                () -> jobOperator.getJobExecution(executionId).getBatchStatus(),
                s -> s == BatchStatus.COMPLETED || s == BatchStatus.FAILED || s == BatchStatus.ABANDONED
                        || s == BatchStatus.STOPPED);

        return jobOperator.getJobExecution(executionId).getBatchStatus();
    }
}
