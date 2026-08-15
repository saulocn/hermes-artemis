package br.com.saulocn.hermes.api.admin;

import br.com.saulocn.hermes.api.admin.broker.BrokerAdmin;
import br.com.saulocn.hermes.api.admin.broker.QueueDepth;
import br.com.saulocn.hermes.api.admin.vo.AdminVOs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

/**
 * Picks the broker adapter and turns its answer — or its failure — into what the console reads.
 *
 * <p>Selection is at runtime, not build time, on purpose: the same hermes-api image runs under
 * both compose profiles, so a build-time choice would need two images.
 *
 * <p>Depth is best-effort by design. Both management APIs live outside this repo's control (the
 * Artemis console comes from a base image whose Jolokia policy is not in the tree), so a failure
 * returns null depths plus the reason instead of failing the request.
 */
@ApplicationScoped
public class BrokerAdminService {

    @Inject
    Logger log;

    @Inject
    Instance<BrokerAdmin> adapters;

    @ConfigProperty(name = "hermes.broker.kind", defaultValue = "artemis")
    String brokerKind;

    public AdminVOs.BrokerStatus status() {
        BrokerAdmin adapter = adapterFor(brokerKind);
        if (adapter == null) {
            // Used to fall through to Artemis, so a typo in BROKER_KIND produced Artemis readings
            // labelled with the typo — a kind the console's own type does not admit.
            String known = adapters.stream().map(BrokerAdmin::kind).sorted().toList().toString();
            return new AdminVOs.BrokerStatus(brokerKind, null, null,
                    "unknown broker kind: " + brokerKind + ", known: " + known);
        }

        try {
            QueueDepth depth = adapter.read();
            return new AdminVOs.BrokerStatus(adapter.kind(), depth.main(), depth.dlq(), null);
        } catch (Exception e) {
            log.warnf("Could not read broker depth from %s: %s", adapter.kind(), e.toString());
            return new AdminVOs.BrokerStatus(adapter.kind(), null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private BrokerAdmin adapterFor(String kind) {
        return adapters.stream()
                .filter(a -> a.kind().equalsIgnoreCase(kind))
                .findFirst()
                .orElse(null);
    }
}
