package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Properties;

@ApplicationScoped
public class MailEnqueuerJob {
    @Inject
    Logger log;

    // SKIP because a run slower than the interval would otherwise overlap the next tick, and both
    // runs would read the same unprocessed rows.
    @Scheduled(every = "{hermes.enqueuer.interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enqueueMails(){
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = new Properties();
        log.info("Iniciando job");
        long executionId = jobOperator.start("mail-enqueuer-chunk", properties);
        log.info("Executando o job:" + executionId);
    }
}
