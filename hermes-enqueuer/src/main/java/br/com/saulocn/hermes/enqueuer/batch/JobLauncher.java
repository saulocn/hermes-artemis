package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * Where every batch start goes through, so that overlapping runs are refused in one place.
 *
 * <p>Deliberately not "so a run cannot overlap itself" — it cannot promise that, and the two
 * paragraphs below say why. What it does give is a single place holding the rule, instead of the
 * scheduler and the REST endpoint each starting jobs on their own.
 *
 * <p>There are two ways to launch: the {@code @Scheduled} tick and the REST endpoint the console
 * calls. {@code ConcurrentExecution.SKIP} guards the scheduler against itself but knows nothing
 * about the manual trigger, so the two could run at once — and they did overlap in practice: both
 * read the same {@code processed = false} rows and publish them. A measured run produced 272,700
 * queued messages for 200,000 recipients, roughly 72,000 duplicate publishes.
 *
 * <p>The remaining hole is that the check and the start are two steps: two triggers arriving in
 * between still both run. Closing it would take a lock the two paths share — a row in the
 * database, or the scheduler's own clustered lock. Left open deliberately, because the atomic
 * claim in the consumer makes the consequence waste rather than incorrectness. If duplicate
 * publishing shows up in a load run again, that lock is the fix, not a wider guard here.
 *
 * <p>The schedules live here too. They used to be two classes carrying one annotation each; the
 * job identity they referenced already lived in this enum.
 */
@ApplicationScoped
public class JobLauncher {

    /**
     * The three names a job answers to, in one place.
     *
     * <p>{@code path} is what appears in the URL, {@code fileName} is what start() takes, and
     * {@code jobId} is what getRunningExecutions() wants — JBeret uses the XML file name for one
     * and the id declared inside it for the other. Keeping them together is what stops a fourth
     * copy appearing as a string map somewhere else.
     */
    public enum Job {
        ENQUEUE("enqueue", "mail-enqueuer-chunk", "mailEnqueuer"),
        FALLBACK("fallback", "mail-fallback-chunk", "mailFallbackEnqueuer");

        private final String path;
        private final String fileName;
        private final String jobId;

        Job(String path, String fileName, String jobId) {
            this.path = path;
            this.fileName = fileName;
            this.jobId = jobId;
        }

        public String path() {
            return path;
        }

        public String fileName() {
            return fileName;
        }

        public static Optional<Job> fromPath(String path) {
            return Arrays.stream(values()).filter(j -> j.path.equals(path)).findFirst();
        }

        public static List<String> paths() {
            return Arrays.stream(values()).map(Job::path).toList();
        }
    }

    @Inject
    Logger log;

    @Scheduled(every = "{hermes.enqueuer.interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void enqueueTick() {
        startIfIdle(Job.ENQUEUE);
    }

    // Fixed interval, unlike the enqueue tick: this is a safety net, not a throughput knob.
    @Scheduled(every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void fallbackTick() {
        startIfIdle(Job.FALLBACK);
    }

    /**
     * Starts the job unless an execution is already running.
     *
     * @return the new execution id, or empty when a run was already in flight
     */
    public OptionalLong startIfIdle(Job job) {
        JobOperator jobOperator = BatchRuntime.getJobOperator();

        if (!runningExecutions(jobOperator, job).isEmpty()) {
            log.infof("Job %s already running, skipping this trigger", job.fileName());
            return OptionalLong.empty();
        }

        long executionId = jobOperator.start(job.fileName(), new Properties());
        log.infof("Job %s started, execution %d", job.fileName(), executionId);
        return OptionalLong.of(executionId);
    }

    /** Empty when there is no such execution. */
    public Optional<Execution> statusOf(long executionId) {
        try {
            var execution = BatchRuntime.getJobOperator().getJobExecution(executionId);
            return Optional.of(new Execution(executionId, execution.getJobName(),
                    execution.getBatchStatus().name()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record Execution(long executionId, String jobName, String status) {
    }

    private java.util.List<Long> runningExecutions(JobOperator jobOperator, Job job) {
        try {
            return jobOperator.getRunningExecutions(job.jobId);
        } catch (jakarta.batch.operations.NoSuchJobException e) {
            // Thrown until the job has run at least once — nothing is running, so go ahead.
            return java.util.List.of();
        }
    }
}
