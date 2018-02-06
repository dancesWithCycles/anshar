package no.rutebanken.anshar.routes.policy;

import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.CamelRouteNames.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;
/**
 * Create policies for enforcing that routes are started as singleton, using Hazelcast for cluster  sync.
 */
@Service
public class SingletonRoutePolicyFactory implements RoutePolicyFactory {

    private static final Logger log = LoggerFactory.getLogger(SingletonRoutePolicyFactory.class);

    @Value("${anshar.route.singleton.policy.ignore:false}")
    private boolean ignorePolicy;

    @Autowired
    private ExtendedHazelcastService hazelcastService;

    /**
     * Create policy ensuring only one route with 'key' is started in cluster.
     */
    private RoutePolicy build(String key) {
        InterruptibleHazelcastRoutePolicy hazelcastRoutePolicy = new InterruptibleHazelcastRoutePolicy(hazelcastService.getHazelcastInstance());
        hazelcastRoutePolicy.setLockMapName("ansharRouteLockMap");
        hazelcastRoutePolicy.setLockKey(key);
        hazelcastRoutePolicy.setLockValue("lockValue");
        hazelcastRoutePolicy.setShouldStopConsumer(false);

        log.info("RoutePolicy: Created HazelcastPolicy for key {}", key);
        return hazelcastRoutePolicy;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition routeDefinition) {
        try {
            if (!ignorePolicy && SINGLETON_ROUTE_DEFINITION_GROUP_NAME.equals(routeDefinition.getGroup())) {
                return build(routeId);
            }
        } catch (Exception e) {
            log.warn("Failed to create singleton route policy", e);
        }
        return null;
    }


}
