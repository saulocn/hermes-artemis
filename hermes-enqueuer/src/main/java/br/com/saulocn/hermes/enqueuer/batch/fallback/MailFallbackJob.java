package br.com.saulocn.hermes.enqueuer.batch.fallback;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.JobExecution;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Properties;

@ApplicationScoped
public class MailFallbackJob {
    @Inject
    Logger log;

    @Scheduled(every = "10m")
    public void enqueueMails(){
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = new Properties();
        log.info("Iniciando job de fallback");
        long executionId = jobOperator.start("mail-fallback-chunk", properties);
        log.info("Executando o job de fallback:" + executionId);
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);
    }
}
