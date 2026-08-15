package br.com.saulocn.hermes.enqueuer.resource;

import br.com.saulocn.hermes.enqueuer.batch.JobLauncher;

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
import java.util.OptionalLong;

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

    private static final Map<String, JobLauncher.Job> JOBS = Map.of(
            "enqueue", JobLauncher.Job.ENQUEUE,
            "fallback", JobLauncher.Job.FALLBACK);

    @Inject
    JobLauncher jobLauncher;

    @POST
    @Path("/{job}")
    public Response start(@PathParam("job") String job) {
        JobLauncher.Job target = JOBS.get(job);
        if (target == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "unknown job: " + job, "known", JOBS.keySet()))
                    .build();
        }

        OptionalLong executionId = jobLauncher.startIfIdle(target);
        if (executionId.isEmpty()) {
            // 409 rather than 200: the caller asked for a run and did not get one. Silently
            // returning success would hide that a run was already publishing the same rows.
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "job already running", "job", job))
                    .build();
        }
        return Response.ok(Map.of("executionId", executionId.getAsLong())).build();
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
