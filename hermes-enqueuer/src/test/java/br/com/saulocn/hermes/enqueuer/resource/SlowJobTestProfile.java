package br.com.saulocn.hermes.enqueuer.resource;

import br.com.saulocn.hermes.enqueuer.batch.BatchTestProfile;

import java.util.Set;

/**
 * Same wiring as {@link BatchTestProfile} (in-memory channel, Postgres via test resource,
 * scheduler disabled), with the publisher swapped for {@link BlockingPublisher}.
 *
 * <p>Needed only by the 409 test in {@code JobResourceIT}, which has to catch a run genuinely in
 * flight rather than assert on timing. Kept as its own profile rather than changing
 * BatchTestProfile so every other batch test keeps its normal, non-blocking publisher.
 */
public class SlowJobTestProfile extends BatchTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(BlockingPublisher.class);
    }
}
