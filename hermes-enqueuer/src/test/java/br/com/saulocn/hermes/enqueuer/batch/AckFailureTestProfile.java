package br.com.saulocn.hermes.enqueuer.batch;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Set;

/**
 * Same as {@link BatchTestProfile}, but the publisher refuses everything.
 *
 * <p>No broker of any kind runs under this profile — the refusal is a swapped adapter, not a
 * container configured to reject.
 */
public class AckFailureTestProfile extends BatchTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(RefusingPublisher.class);
    }

}
