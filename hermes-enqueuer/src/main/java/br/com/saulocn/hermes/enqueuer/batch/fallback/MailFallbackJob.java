package br.com.saulocn.hermes.enqueuer.batch.fallback;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Properties;

@ApplicationScoped
public class MailFallbackJob {
    @Inject
    Logger log;

    // Same reasoning as MailEnqueuerJob: no overlapping runs over the same rows.
    @Scheduled(every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enqueueMails(){
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = new Properties();
        log.info("Iniciando job de fallback");
        long executionId = jobOperator.start("mail-fallback-chunk", properties);
        log.info("Executando o job de fallback:" + executionId);
    }
}
