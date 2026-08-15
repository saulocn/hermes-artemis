package br.com.saulocn.hermes.enqueuer.resource;

import org.jboss.logging.Logger;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Properties;

/**
 * Manual triggers for the batch jobs, so the console does not have to wait out the schedules
 * (30s for enqueue, 10 minutes for fallback).
 *
 * <p>This module had no HTTP surface at all before, despite already shipping quarkus-resteasy and
 * binding a port. The jobs live here because this is where the JobOperator and the
 * reader/processor/writer beans are.
 */
@Path("/jobs")
@Produces(MediaType.APPLICATION_JSON)
public class JobResource {

    private static final Map<String, String> JOBS = Map.of(
            "enqueue", "mail-enqueuer-chunk",
            "fallback", "mail-fallback-chunk");

    @Inject
    Logger log;

    @POST
    @Path("/{job}")
    public Response start(@PathParam("job") String job) {
        String jobName = JOBS.get(job);
        if (jobName == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "unknown job: " + job, "known", JOBS.keySet()))
                    .build();
        }

        // Deliberately fire-and-forget: the scheduled triggers behave the same way, and the
        // caller polls GET /jobs/{executionId} if it wants the outcome.
        long executionId = BatchRuntime.getJobOperator().start(jobName, new Properties());
        log.info("Job " + jobName + " started on demand, execution " + executionId);
        return Response.ok(Map.of("executionId", executionId)).build();
    }

    @GET
    @Path("/{executionId}")
    public Response status(@PathParam("executionId") long executionId) {
        try {
            JobOperator jobOperator = BatchRuntime.getJobOperator();
            var execution = jobOperator.getJobExecution(executionId);
            return Response.ok(Map.of(
                    "executionId", executionId,
                    "jobName", execution.getJobName(),
                    "status", execution.getBatchStatus().name())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "no such execution: " + executionId))
                    .build();
        }
    }
}
