package no.rutebanken.anshar.routes.health;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.HealthCheckKey;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Configuration
public class HealthManager {

    private Logger logger = LoggerFactory.getLogger(HealthManager.class);

    @Autowired
    @Qualifier("getHealthCheckMap")
    private IMap<Enum<HealthCheckKey>, Instant> healthCheckMap;

    @Autowired
    @Qualifier("getUnmappedIds")
    private IMap<String, Map<SubscriptionSetup.SubscriptionType, Set<String>>> unmappedIds;

    @Value("${anshar.admin.health.allowed.inactivity.seconds:999}")
    private long allowedInactivityTime;

    @Value("${anshar.healthcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    public boolean isHazelcastAlive() {
        try {
            healthCheckMap.set(HealthCheckKey.NODE_LIVENESS_CHECK, Instant.now());
            return healthCheckMap.containsKey(HealthCheckKey.NODE_LIVENESS_CHECK);
        } catch (HazelcastInstanceNotActiveException e) {
            logger.warn("HazelcastInstance not active - ", e);
            return false;
        }
    }

    @Bean
    public Instant serverStartTime() {
        if (!healthCheckMap.containsKey(HealthCheckKey.SERVER_START_TIME)) {
            healthCheckMap.set(HealthCheckKey.SERVER_START_TIME, Instant.now());
        }
        return healthCheckMap.get(HealthCheckKey.SERVER_START_TIME);
    }

    public void dataReceived() {
        healthCheckMap.set(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA, Instant.now());
    }


    public boolean isReceivingData() {
        Instant lastReceivedData = healthCheckMap.get(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA);
        if (lastReceivedData != null) {
            long lastReceivedMillis = lastReceivedData.toEpochMilli();

            long seconds = (Instant.now().toEpochMilli() - lastReceivedMillis) / (1000);
            if (seconds > allowedInactivityTime) {
                logger.warn("Last received data: {}, {} seconds ago", lastReceivedData, seconds);
                return false;
            }
        }
        return true;
    }

    public long getSecondsSinceDataReceived() {
        Instant lastReceivedData = healthCheckMap.get(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA);
        if (lastReceivedData != null) {
            long lastReceivedMillis = lastReceivedData.toEpochMilli();

            long seconds = (Instant.now().toEpochMilli() - lastReceivedMillis) / (1000);
            return seconds;
        }
        return -1;
    }

    public Map<SubscriptionSetup.SubscriptionType, Set<String>> getUnmappedIds(String datasetId) {
        return unmappedIds.getOrDefault(datasetId, new HashMap<>());
    }

    public JSONObject getUnmappedIdsAsJson(String datasetId) {
        JSONObject result = new JSONObject();

        Map<SubscriptionSetup.SubscriptionType, Set<String>> dataSetUnmapped = getUnmappedIds(datasetId);

        if (dataSetUnmapped != null) {
            JSONArray typesList = new JSONArray();
            for (SubscriptionSetup.SubscriptionType type : dataSetUnmapped.keySet()) {
                JSONObject typeObject = new JSONObject();
                Set<String> unmappedIdsForType = dataSetUnmapped.get(type);

                JSONArray unmapped = new JSONArray();
                unmapped.addAll(unmappedIdsForType);

                typeObject.put("type", type.toString());
                typeObject.put("count", unmappedIdsForType.size());
                typeObject.put("ids", unmapped);

                typesList.add(typeObject);
            }
            result.put("unmapped", typesList);
        }
        return result;
    }

    public void addUnmappedId(SubscriptionSetup.SubscriptionType type, String datasetId, String id) {
        Map<SubscriptionSetup.SubscriptionType, Set<String>> unmappedIds = getUnmappedIds(datasetId);

        Set<String> ids = unmappedIds.getOrDefault(type, new HashSet<>());
        ids.add(id);
        unmappedIds.put(type, ids);

        this.unmappedIds.set(datasetId, unmappedIds);
    }

    public void removeUnmappedId(SubscriptionSetup.SubscriptionType type, String datasetId, String id) {
        Map<SubscriptionSetup.SubscriptionType, Set<String>> unmappedIds = getUnmappedIds(datasetId);

        Set<String> ids = unmappedIds.getOrDefault(type, new HashSet<>());
        ids.remove(id);
        unmappedIds.put(type, ids);

        this.unmappedIds.set(datasetId, unmappedIds);
    }
}
