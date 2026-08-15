package br.com.saulocn.hermes.enqueuer.batch;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;

import java.time.Duration;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Starts a JBeret job the way the @Scheduled triggers do, and blocks until it finishes. */
final class BatchJobs {

    private BatchJobs() {
    }

    static void runToCompletion(String jobName) {
        BatchStatus status = runToTerminalStatus(jobName);
        assertEquals(BatchStatus.COMPLETED, status, "job " + jobName + " ended with status " + status);
    }

    /**
     * Runs the job and returns whatever terminal status it reaches. Use this when the failure
     * path is the subject of the test — waiting for COMPLETED would just burn the timeout.
     */
    static BatchStatus runToTerminalStatus(String jobName) {
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        long executionId = jobOperator.start(jobName, new Properties());

        await().atMost(Duration.ofSeconds(60)).until(
                () -> jobOperator.getJobExecution(executionId).getBatchStatus(),
                s -> s == BatchStatus.COMPLETED || s == BatchStatus.FAILED || s == BatchStatus.ABANDONED
                        || s == BatchStatus.STOPPED);

        return jobOperator.getJobExecution(executionId).getBatchStatus();
    }
}
