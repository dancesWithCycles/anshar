package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.anshar.routes.outbound.SiriHelper.containsValues;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.getFilter;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.splitDeliveries;

@Service
public class CamelRouteManager implements CamelContextAware {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    protected static CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }


    /**
     * Creates a new ad-hoc route that sends the SIRI payload to supplied address, executes it, and finally terminates and removes it.
     * @param payload
     * @param subscriptionRequest
     */
    public void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

        List<Siri> splitSiri = splitDeliveries(filteredPayload, 1000);

        logger.info("Object split into {} deliveries.", splitSiri.size());

        for (Siri siri : splitSiri) {
            Thread r = new Thread() {
                @Override
                public void run() {
                    try {

                        SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress);
                        String routeId = addSiriPushRoute(siriPushRouteBuilder);
                        executeSiriPushRoute(siri, siriPushRouteBuilder.getRouteName());
                        stopAndRemoveSiriPushRoute(routeId);
                    } catch (Exception e) {
                        if (e.getCause() instanceof SocketException) {
                            logger.info("Recipient is unreachable - ignoring");
                        } else {
                            logger.warn("Exception caught when pushing SIRI-data", e);
                        }
                    }
                }
            };
            r.start();
        }
    }

    private String addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        camelContext.addRoutes(route);
        logger.trace("Route added - CamelContext now has {} routes", camelContext.getRoutes().size());
        return route.getDefinition().getId();
    }

    private boolean stopAndRemoveSiriPushRoute(String routeId) throws Exception {
        RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
        camelContext.removeRouteDefinition(routeDefinition);
        logger.trace("Route removed - CamelContext now has {} routes", camelContext.getRoutes().size());
        return true;
    }


    private void executeSiriPushRoute(Siri payload, String routeName) throws JAXBException {
        if (!serviceDeliveryContainsData(payload)) {
            return;
        }
        String xml = SiriXml.toXml(payload);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, xml);
    }

    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                boolean containsSXdata = deliveryStructure.getSituations() != null &&
                        containsValues(deliveryStructure.getSituations().getPtSituationElements());
                logger.info("Contains SX-data: [{}]", containsSXdata);
                return containsSXdata;
            }

            if (containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                boolean containsVMdata = deliveryStructure.getVehicleActivities() != null &&
                        containsValues(deliveryStructure.getVehicleActivities());
                logger.info("Contains VM-data: [{}]", containsVMdata);
                return containsVMdata;
            }

            if (containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                boolean containsETdata = (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
                logger.info("Contains ET-data: [{}]", containsETdata);
                return containsETdata;
            }
        }
        return true;
    }

    private class SiriPushRouteBuilder extends RouteBuilder {

        private String remoteEndPoint;
        private RouteDefinition definition;
        private String routeName;

        public SiriPushRouteBuilder(String remoteEndPoint) {
            this.remoteEndPoint=remoteEndPoint;
        }

        @Override
        public void configure() throws Exception {

            if (remoteEndPoint.startsWith("http://")) {
                //Translating URL to camel-format
                remoteEndPoint = remoteEndPoint.substring("http://".length());
            }

            routeName = String.format("direct:%s", UUID.randomUUID().toString());

            errorHandler(
                    deadLetterChannel("activemq:queue:error")
            );

            from("activemq:queue:error")
                    .routeId("errorhandler:"+remoteEndPoint) //One errorhandler per subscription/endpoint
                    .log("Error sending data to url " + remoteEndPoint);

            definition = from(routeName)
                    .log(LoggingLevel.INFO, "POST data to " + remoteEndPoint)
                    .setHeader("CamelHttpMethod", constant("POST"))
                    .marshal().string("UTF-8")
                    .to("http4://" + remoteEndPoint);

        }

        public RouteDefinition getDefinition() {
            return definition;
        }

        public String getRouteName() {
            return routeName;
        }
    }
}
