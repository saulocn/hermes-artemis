package br.com.saulocn.hermes.mailer.batch;

import io.quarkus.scheduler.Scheduled;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.JobExecution;
import javax.enterprise.context.ApplicationScoped;
import java.util.Properties;

@ApplicationScoped
public class MailEnqueuerJob {

    @Scheduled(every = "5m")
    public void enqueueMails(){
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        Properties properties = new Properties();
        long executionId = jobOperator.start("mail-enqueuer-chunk", properties);
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);
        System.out.println("Executando o job..." + executionId);
    }
}
