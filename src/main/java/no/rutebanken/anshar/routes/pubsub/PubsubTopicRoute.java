package no.rutebanken.anshar.routes.pubsub;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PubsubTopicRoute extends RouteBuilder {

    @Value("${anshar.outbound.camel.route.topic.et.name}")
    private String etTopic;

    @Value("${anshar.outbound.camel.route.topic.vm.name}")
    private String vmTopic;

    @Value("${anshar.outbound.camel.route.topic.sx.name}")
    private String sxTopic;

    @Value("${anshar.outbound.pubsub.topic.enabled}")
    private boolean pushToTopicEnabled;

    private AtomicInteger counter = new AtomicInteger();

    @Override
    public void configure() {
        if (pushToTopicEnabled) {

            /**
             * Splits SIRI ET-ServiceDelivery into singular messages (i.e. one ET-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.estimated_timetable")
                    .to("direct:siri.transform.data")
                    .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                    .split().tokenizeXML("Siri").streaming()
                    .to("direct:map.jaxb.to.protobuf")
                    .wireTap("direct:log.pubsub.traffic")
                    .to(etTopic)
            ;

            /**
             * Splits SIRI VM-ServiceDelivery into singular messages (i.e. one VM-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.vehicle_monitoring")
                    .to("direct:siri.transform.data")
                    .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                    .split().tokenizeXML("Siri").streaming()
                    .to("direct:map.jaxb.to.protobuf")
                    .wireTap("direct:log.pubsub.traffic")
                    .to(vmTopic)
            ;

            /**
             * Splits SIRI SX-ServiceDelivery into singular messages (i.e. one SX-message per ServiceDelivery), converts
             * message to protobuf, and posts to Cloud Pubsub
             */
            from("direct:send.to.pubsub.topic.alerts")
                    .to("direct:siri.transform.data")
                    .to("xslt:xsl/splitAndFilterNotMonitored.xsl")
                    .split().tokenizeXML("Siri").streaming()
                    .to("direct:map.jaxb.to.protobuf")
                    .wireTap("direct:log.pubsub.traffic")
                    .to(sxTopic)
            ;

            /**
             * Logs traffic periodically
             */
            from("direct:log.pubsub.traffic")
                    .routeId("log.pubsub")
                    .process(p -> {
                        if (counter.incrementAndGet() % 1000 == 0) {
                            p.getOut().setHeader("counter", counter.get());
                            p.getOut().setBody(p.getIn().getBody());
                        }
                    })
                    .choice()
                    .when(header("counter").isNotNull())
                    .log("Pubsub: Published ${header.counter} updates")
                    .endChoice()
                    .end();
        }
    }
}

