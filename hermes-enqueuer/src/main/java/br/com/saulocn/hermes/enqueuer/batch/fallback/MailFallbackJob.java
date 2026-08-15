package br.com.saulocn.hermes.enqueuer.batch.fallback;

import br.com.saulocn.hermes.enqueuer.batch.JobLauncher;
import io.quarkus.scheduler.Scheduled;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MailFallbackJob {

    @Inject
    JobLauncher jobLauncher;

    // Same reasoning as MailEnqueuerJob: SKIP covers the scheduler, the launcher covers the rest.
    @Scheduled(every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enqueueMails() {
        jobLauncher.startIfIdle(JobLauncher.Job.FALLBACK);
    }
}
