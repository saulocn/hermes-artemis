package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Read-mostly endpoints backing the operator console. */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final int MAX_PAGE_SIZE = 200;

    @Inject
    DashboardService dashboard;

    @Inject
    BrokerAdminService broker;

    @Inject
    JobTriggerService jobs;

    @GET
    @Path("/stats")
    public AdminVOs.Stats stats() {
        return dashboard.stats();
    }

    @GET
    @Path("/throughput")
    public AdminVOs.Throughput throughput(@QueryParam("minutes") @DefaultValue("60") int minutes) {
        // Bounded so a caller cannot ask for an arbitrarily long scan.
        return dashboard.throughput(Math.min(Math.max(minutes, 1), 1440));
    }

    @GET
    @Path("/broker")
    public AdminVOs.BrokerStatus broker() {
        return broker.status();
    }

    @GET
    @Path("/messages")
    public AdminVOs.Page<AdminVOs.MessageSummary> messages(@QueryParam("page") @DefaultValue("0") int page,
                                                           @QueryParam("size") @DefaultValue("20") int size,
                                                           @QueryParam("q") String q) {
        return dashboard.messages(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), q);
    }

    @GET
    @Path("/recipients")
    public Response recipients(@QueryParam("email") String email,
                               @QueryParam("state") String state,
                               @QueryParam("page") @DefaultValue("0") int page,
                               @QueryParam("size") @DefaultValue("20") int size) {
        try {
            return Response.ok(dashboard.recipients(email, state,
                    Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))).build();
        } catch (IllegalArgumentException e) {
            // A state the server does not know is a bad request, not an empty result: answering
            // 200 with zero rows is indistinguishable from "no recipient is in that state".
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", e.getMessage(),
                            "states", RecipientState.wireNames()))
                    .build();
        }
    }

    @POST
    @Path("/recipients/{id}/retry")
    public Response retry(@PathParam("id") Long id) {
        if (!dashboard.retry(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new AdminVOs.Retried(id, true)).build();
    }

    @POST
    @Path("/jobs/enqueue")
    public Response triggerEnqueue() {
        return trigger("enqueue");
    }

    @POST
    @Path("/jobs/fallback")
    public Response triggerFallback() {
        return trigger("fallback");
    }

    private Response trigger(String job) {
        try {
            return Response.ok(jobs.trigger(job)).build();
        } catch (JobTriggerService.JobAlreadyRunningException e) {
            // 409 all the way to the console: a declined trigger is not an error, and the screen
            // should say "already running" rather than show a failure banner.
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        } catch (JobTriggerService.EnqueuerUnreachableException e) {
            // 502, not 500: nothing here failed. This api is fine and the operator's request was
            // fine — the service behind it did not answer. Left as an unhandled exception it
            // reached the console as a raw stack trace, which points the operator at the wrong
            // service. Also says plainly that nothing was started, so a retry is safe.
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(java.util.Map.of(
                            "error", e.getMessage(),
                            "started", false)).build();
        }
    }
}
