package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import br.com.saulocn.hermes.enqueuer.batch.JobLauncher;
import io.quarkus.scheduler.Scheduled;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MailEnqueuerJob {

    @Inject
    JobLauncher jobLauncher;

    // SKIP keeps one tick from overlapping the next. It does not know about the REST trigger,
    // which is why the launcher checks for a running execution as well.
    @Scheduled(every = "{hermes.enqueuer.interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enqueueMails() {
        jobLauncher.startIfIdle(JobLauncher.Job.ENQUEUE);
    }
}
