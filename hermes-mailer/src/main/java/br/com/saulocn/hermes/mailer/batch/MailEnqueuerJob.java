package br.com.saulocn.hermes.mailer.batch;

import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.JobExecution;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Properties;

@ApplicationScoped
public class MailEnqueuerJob {
    @Inject
    Logger log;

    @Scheduled(every = "10s")
    public void enqueueMails(){
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = new Properties();
        log.info("Iniciando job");
        long executionId = jobOperator.start("mail-enqueuer-chunk", properties);
        log.info("Executando o job..." + executionId);
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);
        log.info("Executando o job2..." + executionId);
    }
}
