package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.vo.AdminVOs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Aggregate queries for the operator console.
 *
 * <p>Kept apart from {@code MessageService} because that one sits on the ingest path: the console
 * polls these, and mixing them would make it easy to slow ingestion down by accident. They share
 * a datasource, which is why its pool was raised from 2.
 */
@ApplicationScoped
public class DashboardService {

    @Inject
    EntityManager em;


    @Transactional
    public AdminVOs.Stats stats() {
        // One pass over recipient instead of one count per state. The predicates come from
        // RecipientState so that this and the filter in recipients() cannot disagree.
        Object[] row = (Object[]) em.createNativeQuery("""
                select
                  %s,
                  min(created_on) filter (where not recipient_sent)
                from hermes.recipient
                """.formatted(RecipientState.countColumns())).getSingleResult();

        // Positional array from countColumns() emits in enum order; EnumMap re-keys by state. Safety
        // comes from both sides using the same order (enum.values()).
        Map<RecipientState, Long> counts = new EnumMap<>(RecipientState.class);
        RecipientState[] states = RecipientState.values();
        for (int i = 0; i < states.length; i++) {
            counts.put(states[i], ((Number) row[i]).longValue());
        }
        Object oldestCreatedOn = row[states.length];

        long totalMessages = ((Number) em.createNativeQuery("select count(*) from hermes.message")
                .getSingleResult()).longValue();

        Long oldestPendingSeconds = null;
        if (oldestCreatedOn != null) {
            LocalDateTime oldest = toLocalDateTime(oldestCreatedOn);
            oldestPendingSeconds = java.time.Duration.between(oldest, LocalDateTime.now()).getSeconds();
        }

        return new AdminVOs.Stats(
                counts.get(RecipientState.PENDING),
                counts.get(RecipientState.IN_FLIGHT),
                counts.get(RecipientState.FAILING),
                counts.get(RecipientState.DELIVERED),
                totalMessages,
                oldestPendingSeconds);
    }


    @Transactional
    public AdminVOs.Page<AdminVOs.MessageSummary> messages(int page, int size, String query) {
        String filter = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase() + "%";

        var countQuery = em.createNativeQuery("""
                select count(*) from hermes.message m
                where (cast(:filter as text) is null or lower(m.message_title) like cast(:filter as text))
                """).setParameter("filter", filter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select m.message_id, m.message_title, m.content_type, m.created_on,
                       count(r.recipient_id), count(r.recipient_id) filter (where r.recipient_sent)
                from hermes.message m
                left join hermes.recipient r on r.message_id = m.message_id
                where (cast(:filter as text) is null or lower(m.message_title) like cast(:filter as text))
                group by m.message_id, m.message_title, m.content_type, m.created_on
                order by m.message_id desc
                """)
                .setParameter("filter", filter)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        List<AdminVOs.MessageSummary> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new AdminVOs.MessageSummary(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    (String) row[2],
                    toLocalDateTime(row[3]),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).longValue()));
        }
        return new AdminVOs.Page<>(page, size, total, items);
    }

    @Transactional
    public AdminVOs.Page<AdminVOs.RecipientSummary> recipients(String email, String state, int page, int size) {
        String filter = (email == null || email.isBlank()) ? null : "%" + email.toLowerCase() + "%";
        // An unknown state is rejected rather than ignored: silently answering with every row is
        // how `state=failing` looked like it worked while matching no arm at all.
        String stateFilter = (state == null || state.isBlank()) ? null
                : RecipientState.fromWireName(state)
                        .orElseThrow(() -> new IllegalArgumentException("unknown recipient state: " + state))
                        .wireName();

        String where = """
                where (cast(:filter as text) is null or lower(recipient_mail) like cast(:filter as text))
                  and (cast(:state as text) is null
                       %s)
                """.formatted(RecipientState.filterArms());

        long total = ((Number) em.createNativeQuery("select count(*) from hermes.recipient " + where)
                .setParameter("filter", filter)
                .setParameter("state", stateFilter)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select recipient_id, recipient_mail, message_id, recipient_processed,
                       recipient_sent, recipient_attempts, created_on
                from hermes.recipient
                """ + where + " order by recipient_id desc")
                .setParameter("filter", filter)
                .setParameter("state", stateFilter)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        List<AdminVOs.RecipientSummary> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new AdminVOs.RecipientSummary(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    row[2] == null ? null : ((Number) row[2]).longValue(),
                    (Boolean) row[3],
                    (Boolean) row[4],
                    ((Number) row[5]).intValue(),
                    toLocalDateTime(row[6])));
        }
        return new AdminVOs.Page<>(page, size, total, items);
    }

    /**
     * Clears `processed` and `published_on` so the enqueuer picks the recipient up again.
     * A targeted update, for the same reason the enqueuer and mailer use one: writing
     * the whole row here would race with them and clobber `sent`.
     *
     * <p>The attempt counter is cleared with it. An operator retrying a row is saying the last
     * failures no longer describe it — leaving the count would put the row straight back in
     * "failing" the moment the enqueuer republishes it, before anything has been tried again.
     *
     * <p>`claimed_on` is NOT cleared: the mailer may be mid-delivery for this recipient,
     * and clearing it would break the `claimed_on IS NOT NULL ⟺ recipient_sent` invariant.
     */
    @Transactional
    public boolean retry(Long recipientId) {
        // Use native query to clear published_on alongside the JPA-friendly fields.
        // This is driven through QuarkusTransaction.requiringNew() in tests because
        // the annotation does not propagate when called from another test method.
        int updated = em.createNativeQuery("""
                update hermes.recipient
                   set recipient_processed = false, recipient_attempts = 0, published_on = null
                 where recipient_id = :id
                """)
                .setParameter("id", recipientId)
                .executeUpdate();
        return updated > 0;
    }

    /**
     * Hibernate 6 hands back LocalDateTime for timestamp columns, but the exact type depends on
     * the query shape (date_trunc, min(), a plain column), so accept either rather than betting
     * on one and failing at runtime.
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("unexpected timestamp type: " + value.getClass());
    }
}
