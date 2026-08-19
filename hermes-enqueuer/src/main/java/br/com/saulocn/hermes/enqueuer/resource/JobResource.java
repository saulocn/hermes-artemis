package br.com.saulocn.hermes.enqueuer.resource;

import br.com.saulocn.hermes.enqueuer.batch.JobLauncher;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Manual triggers for the batch jobs, so the console does not have to wait out the schedules.
 *
 * <p>The jobs live in this module because this is where the JobOperator and the
 * reader/writer beans are. Everything about a job's identity comes from {@link JobLauncher.Job};
 * this resource used to keep its own string-to-enum map, which was a second place to edit.
 */
@Path("/jobs")
@Produces(MediaType.APPLICATION_JSON)
public class JobResource {

    @Inject
    JobLauncher jobLauncher;

    @POST
    @Path("/{job}")
    public Response start(@PathParam("job") String job) {
        Optional<JobLauncher.Job> target = JobLauncher.Job.fromPath(job);
        if (target.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "unknown job: " + job, "known", JobLauncher.Job.paths()))
                    .build();
        }

        OptionalLong executionId = jobLauncher.startIfIdle(target.get());
        if (executionId.isEmpty()) {
            // 409, not 200: the caller asked for a run and did not get one. Answering success
            // would hide that another run is already publishing the same rows.
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "job already running", "job", job))
                    .build();
        }
        return Response.ok(Map.of("executionId", executionId.getAsLong())).build();
    }

    @GET
    @Path("/{executionId}")
    public Response status(@PathParam("executionId") long executionId) {
        return jobLauncher.statusOf(executionId)
                .map(e -> Response.ok(e).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "no such execution: " + executionId)).build());
    }
}
