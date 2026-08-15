package br.com.saulocn.hermes.api.admin;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a recipient row means, and the one place that decides it.
 *
 * <p>The schema stores two booleans and a counter; "pending", "in flight", "failing" and
 * "delivered" are readings of them. That reading used to be written out by hand in every place
 * that needed it — the counting query, the filter query, the wire names, the labels, the dropdown
 * — so adding {@code failing} landed in four of eight places and the filter was left behind: the
 * console showed a count with no way to list the rows behind it.
 *
 * <p>Here the wire name and the predicate travel together. A new state is one line, and the
 * counting query, the filter and the dropdown all pick it up.
 */
public enum RecipientState {

    PENDING("pending", "not recipient_processed and not recipient_sent"),

    IN_FLIGHT("inFlight", "recipient_processed and not recipient_sent"),

    /** A subset of IN_FLIGHT: published, undelivered, and known to have thrown at least once. */
    FAILING("failing", "recipient_processed and not recipient_sent and recipient_attempts > 0"),

    DELIVERED("delivered", "recipient_sent");

    private final String wireName;
    private final String predicate;

    RecipientState(String wireName, String predicate) {
        this.wireName = wireName;
        this.predicate = predicate;
    }

    /** The name the HTTP API and the console use. Not {@code name()}: that one is SCREAMING_CASE. */
    public String wireName() {
        return wireName;
    }

    /** SQL over hermes.recipient, with no table alias, true exactly for rows in this state. */
    public String predicate() {
        return predicate;
    }

    public static Optional<RecipientState> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(s -> s.wireName.equals(wireName)).findFirst();
    }

    /** Every state the API accepts, so a rejection can say what it would have taken. */
    public static java.util.List<String> wireNames() {
        return Arrays.stream(values()).map(RecipientState::wireName).toList();
    }

    /** {@code count(*) filter (...)} for every state, in declaration order. */
    static String countColumns() {
        return Arrays.stream(values())
                .map(s -> "count(*) filter (where " + s.predicate + ") as " + s.name().toLowerCase())
                .collect(Collectors.joining(",\n  "));
    }

    /**
     * The arms of a {@code :state} filter. Null or unrecognised state is handled by the caller's
     * {@code is null} arm; a state that exists here always has a matching arm, which is the bug
     * this replaces.
     */
    static String filterArms() {
        return Arrays.stream(values())
                .map(s -> "or (cast(:state as text) = '" + s.wireName + "' and (" + s.predicate + "))")
                .collect(Collectors.joining("\n       "));
    }
}
