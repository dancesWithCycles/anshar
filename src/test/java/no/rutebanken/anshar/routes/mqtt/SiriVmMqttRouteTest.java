package no.rutebanken.anshar.routes.mqtt;

import javafx.util.Pair;
import org.json.JSONObject;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.math.BigDecimal;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import static org.junit.Assert.*;

public class SiriVmMqttRouteTest {

    @Test
    public void testTopicFormat() {

        ZonedDateTime dateTime = ZonedDateTime.of(2017, 12, 24, 10, 11, 4, 0, ZoneId.of("GMT"));

        VehicleActivityStructure vehicle = createVehicle(dateTime, "veh123", "RUT:Line:7890", "Nettbus",
                1, "NSR:Quay:4321", 59.10234567, 10.98765421, 123, "DesttNNx tehd",
                "7890", "not used in this test", 1);

        String datasetId = "RUT";
        Pair<String, String> message = new SiriVmMqttRoute().getMessage(datasetId, vehicle);
        String topic = message.getKey();

        assertEquals("/hfp/journey/bus/RUTveh123/RUT:Line:7890/1/DesttNNx tehd/1011/NSR:Quay:4321/59;10/19/08/27/", topic);
    }

    @Test
    public void testMessage() {
        ZonedDateTime dateTime = ZonedDateTime.of(2017, 12, 24, 9, 37, 4, 0, ZoneId.of("GMT"));
        VehicleActivityStructure vehicle = createVehicle(dateTime, "78123", "RUT:Line:0037", "Nobina",
                2, "NSR:Quay:6201", 59.10234566, 10.98765422, 203, "Helsfyr T",
                "37", "Nydalen T", 18);

        String datasetId = "RUT";
        Pair<String, String> message = new SiriVmMqttRoute().getMessage(datasetId, vehicle);
        String msg = message.getValue();

        JSONObject obj = new JSONObject(msg).getJSONObject(VehiclePosition.ROOT);

        assertEquals("37", obj.get(VehiclePosition.DESIGNATION));
        assertEquals("2", obj.get(VehiclePosition.DIRECTION));
        assertEquals("Nobina", obj.get(VehiclePosition.OPERATOR));
        assertEquals("RUT78123", obj.get(VehiclePosition.VEHICLE_ID));
        assertEquals("2017-12-24T09:37:04Z", obj.get(VehiclePosition.TIMESTAMP));
        assertEquals(1514108224, obj.get(VehiclePosition.TSI));
        assertEquals(59.10234566, obj.get(VehiclePosition.LATITUDE));
        assertEquals(10.98765422, obj.get(VehiclePosition.LONGITUDE));
        assertEquals(203, obj.get(VehiclePosition.DELAY));
        assertEquals("2017-12-24", obj.get(VehiclePosition.ODAY));
        assertEquals("Nydalen T -> Helsfyr T", obj.get(VehiclePosition.JOURNEY));
        assertEquals("RUT:Line:0037", obj.get(VehiclePosition.LINE));
        assertEquals("0937", obj.get(VehiclePosition.STARTTIME));
        assertEquals(18, obj.get(VehiclePosition.STOP_INDEX));
        assertEquals("rutebanken", obj.get(VehiclePosition.SOURCE));
    }

    private VehicleActivityStructure createVehicle(ZonedDateTime dateTime, String vehicleRef, String line,
                                                   String operator, int direction,
                                                   String nextStop, // -> onwardscalls[0]
                                                   double lat, double lng, int delay,
                                                   String destinationName, // -> destinationNames[0]
                                                   String publishedName, // -> publishedLineNames[0]
                                                   String origin, // -> orginNames[0]
                                                   int stopIndex) { // -> monitoredCalls[0]
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(dateTime);
        element.setValidUntilTime(dateTime.plusMinutes(10));

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleRef);
        vehicleJourney.setVehicleRef(vRef);

        LineRef lineRef = new LineRef();
        lineRef.setValue(line);
        vehicleJourney.setLineRef(lineRef);

        OperatorRefStructure operatorRef = new OperationalUnitRefStructure();
        operatorRef.setValue(operator);
        vehicleJourney.setOperatorRef(operatorRef);

        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue("" + direction);
        vehicleJourney.setDirectionRef(directionRef);

        vehicleJourney.setOriginAimedDepartureTime(dateTime);

        StopPointRef stopPointRef = new StopPointRef();
        stopPointRef.setValue(nextStop);
        OnwardCallStructure onwardCall = new OnwardCallStructure();
        onwardCall.setStopPointRef(stopPointRef);
        OnwardCallsStructure onwardCalls = new OnwardCallsStructure();
        onwardCalls.getOnwardCalls().add(onwardCall);
        vehicleJourney.setOnwardCalls(onwardCalls);

        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(lat));
        location.setLongitude(BigDecimal.valueOf(lng));
        vehicleJourney.setVehicleLocation(location);

        vehicleJourney.setDelay(new Duration() {
            @Override
            public int getSeconds() {
                return delay;
            }

            public int getSign() { return 0; }
            public Number getField(DatatypeConstants.Field field) { return null; }
            public boolean isSet(DatatypeConstants.Field field) { return false; }
            public Duration add(Duration rhs) { return null; }
            public void addTo(Calendar calendar) { }
            public Duration multiply(BigDecimal factor) { return null; }
            public Duration negate() { return null; }
            public Duration normalizeWith(Calendar startTimeInstant) { return null; }
            public int compare(Duration duration) { return 0; }
            public int hashCode() { return 0; }
        });

        NaturalLanguageStringStructure destinationNames = new NaturalLanguageStringStructure();
        destinationNames.setValue(destinationName);
        vehicleJourney.getDestinationNames().add(destinationNames);

        NaturalLanguageStringStructure publishedLineNames = new NaturalLanguageStringStructure();
        publishedLineNames.setValue(publishedName);
        vehicleJourney.getPublishedLineNames().add(publishedLineNames);

        NaturalLanguagePlaceNameStructure originNames = new NaturalLanguagePlaceNameStructure();
        originNames.setValue(origin);
        vehicleJourney.getOriginNames().add(originNames);

        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        monitoredCall.setVisitNumber(BigInteger.valueOf(stopIndex));
        vehicleJourney.setMonitoredCall(monitoredCall);

        /*
        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);
        */

        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }
}
