package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Service
@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.healthcheck.hubot.url}")
    private String hubotUrl;

    @Value("${anshar.healthcheck.hubot.payload.source}")
    private String hubotSource;

    @Value("${anshar.healthcheck.hubot.payload.icon}")
    private String hubotIcon;

    @Value("${anshar.healthcheck.hubot.payload.message}")
    private String hubotMessage;

    @Value("${anshar.healthcheck.hubot.payload.template}")
    private String hubotTemplate;

    @Value("${anshar.healthcheck.hubot.allowed.inactivity.minutes:10}")
    private int allowedInactivityMinutes;

    @Value("${anshar.healthcheck.hubot.start.time}")
    private String startMonitorTimeStr;
    private LocalTime startMonitorTime;

    @Value("${anshar.healthcheck.hubot.end.time}")
    private String endMonitorTimeStr;
    private LocalTime endMonitorTime;

    @Autowired
    HealthManager healthManager;

    @Autowired
    SubscriptionManager subscriptionManager;

    public static boolean triggerRestart;

    @PostConstruct
    private void init() {
        startMonitorTime = LocalTime.parse(startMonitorTimeStr);
        endMonitorTime = LocalTime.parse(endMonitorTimeStr);
    }

    @Override
    public void configure() throws Exception {

        //To avoid large stacktraces in the log when fetching data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
                .routeId("health.favicon")
        ;

        // Application is ready to accept traffic
        from("jetty:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
                .routeId("health.ready")
        ;

        // Application is (still) alive and well
        from("jetty:http://0.0.0.0:" + inboundPort + "/up")
                //TODO: On error - POST to hubot
                // Ex: wget --post-data='{"source":"otp", "message":"Downloaded file is empty or not present. This makes OTP fail! Please check logs"}' http://hubot/hubot/say/
                .choice()
                .when(p -> triggerRestart)
                    .log("Application triggered restart")
                    .setBody(constant("Restart requested"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .endChoice()
                .when(p -> !healthManager.isHazelcastAlive())
                    .log("Hazelcast is shut down")
                    .setBody(simple("Hazelcast is shut down"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .endChoice()
                .when(p -> !healthManager.isHealthCheckRunning())
                    .log("HealthCheck has stopped")
                    .setBody(simple("HealthCheck has stopped"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                .endChoice()
                .otherwise()
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.up")
        ;

        from("jetty:http://0.0.0.0:" + inboundPort + "/healthy")
                .choice()
                .when(p -> !healthManager.isReceivingData())
                    .process(p -> {
                        p.getOut().setBody("Server has not received data for " + healthManager.isReceivingData() + " seconds.");
                    })
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                    .log("Server is not receiving data")
                .endChoice()
                .when(p -> getAllUnhealthySubscriptions() != null && !getAllUnhealthySubscriptions().isEmpty())
                    .process(p -> {
                        Set<String> unhealthySubscriptions = getAllUnhealthySubscriptions();

                        if (unhealthySubscriptions.isEmpty()) {
                            //All green - clear status
                            unhealthySubscriptionsAlreadyNotified.clear();
                        } else {
                            //Avoid notifying multiple times for same subscriptions
                            unhealthySubscriptions.removeAll(unhealthySubscriptionsAlreadyNotified);
                            //Keep
                            unhealthySubscriptionsAlreadyNotified.addAll(unhealthySubscriptions);
                        }
                        if (!unhealthySubscriptions.isEmpty()) {
                            String message = MessageFormat.format(hubotMessage, unhealthySubscriptions);
                            String jsonPayload = "{" + MessageFormat.format(hubotTemplate, hubotSource, hubotIcon, message) + "}";

                            if (LocalTime.now().isAfter(startMonitorTime) &&
                                    LocalTime.now().isBefore(endMonitorTime)) {
                                p.getOut().setBody("{" + jsonPayload +"}");
                                p.getOut().setHeader("notify-target", "hubot");
                                logger.warn("Healtchckeck: Subscriptions not receiving data - notifying hubot:" + jsonPayload);
                            } else {
                                p.getOut().setBody("Subscriptions not receiving data - NOT notifying hubot:" + jsonPayload);
                                logger.warn("Healtchckeck: Subscriptions not receiving data - NOT notifying hubot: " + unhealthySubscriptions);
                            }
                        } else if (unhealthySubscriptionsAlreadyNotified.isEmpty() &&
                                unhealthySubscriptions.isEmpty()) {
                            //All clear
                            logger.info("Healtchckeck: Subscriptions are back to normal");
                        }
                    })
                    .when(header("notify-target").isEqualTo("hubot"))
//                    .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.JSON_UTF_8))
//                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
//                    .to(hubotUrl)
                    .endChoice()
                .endChoice()
                .otherwise()
                    .setBody(simple("OK"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .end()
                .routeId("health.is.healthy")
        ;

    }
    Set<String> unhealthySubscriptionsAlreadyNotified = new HashSet<>();

    private Set<String> getAllUnhealthySubscriptions() {
        Set<String> unhealthySubscriptions = subscriptionManager.getAllUnhealthySubscriptions(allowedInactivityMinutes*60);
        return unhealthySubscriptions;
    }
}
