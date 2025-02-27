package fi.livi.digitraffic.tie.scheduler;

import static fi.livi.digitraffic.tie.controller.RoadStationState.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.client.support.destination.DestinationProvider;

import fi.livi.digitraffic.tie.dto.v1.TmsRoadStationSensorDto;
import fi.livi.digitraffic.tie.dto.v1.TmsRoadStationsSensorsMetadata;
import fi.livi.digitraffic.tie.metadata.geojson.tms.TmsStationFeature;
import fi.livi.digitraffic.tie.metadata.geojson.tms.TmsStationFeatureCollection;
import fi.livi.digitraffic.tie.model.CollectionStatus;
import fi.livi.digitraffic.tie.model.VehicleClass;
import fi.livi.digitraffic.tie.service.RoadStationSensorService;
import fi.livi.digitraffic.tie.service.v1.lotju.LotjuLAMMetatiedotServiceEndpointMock;
import fi.livi.digitraffic.tie.service.v1.lotju.LotjuTmsStationMetadataClient;
import fi.livi.digitraffic.tie.service.v1.tms.TmsSensorUpdater;
import fi.livi.digitraffic.tie.service.v1.tms.TmsStationService;
import fi.livi.digitraffic.tie.service.v1.tms.TmsStationUpdater;
import fi.livi.digitraffic.tie.service.v1.tms.TmsStationsSensorsUpdater;

public class TmsStationMetadataUpdateJobTest extends AbstractMetadataUpdateJobTest {

    private static final Logger log = LoggerFactory.getLogger(TmsStationMetadataUpdateJobTest.class);

    @Autowired
    private TmsSensorUpdater tmsSensorUpdater;

    @Autowired
    private TmsStationsSensorsUpdater tmsStationsSensorsUpdater;

    @Autowired
    private TmsStationUpdater tmsStationUpdater;

    @Autowired
    private TmsStationService tmsStationService;

    @Autowired
    private RoadStationSensorService roadStationSensorService;

    @Autowired
    private LotjuLAMMetatiedotServiceEndpointMock lotjuLAMMetatiedotServiceMock;

    @Autowired
    private LotjuTmsStationMetadataClient lotjuTmsStationMetadataClient;

    private DestinationProvider originalDestinationProvider;

    @BeforeEach
    public void setFirstDestinationProviderForLotjuClients() {
        setLotjuClientFirstDestinationProviderAndSaveOroginalToMap(lotjuTmsStationMetadataClient);
    }

    @AfterEach
    public void restoreOriginalDestinationProviderForLotjuClients() {
        restoreLotjuClientDestinationProvider(lotjuTmsStationMetadataClient);
    }


    @AfterEach
    public void restoreLotjuClient() {
        final LotjuTmsStationMetadataClient lotjuClient = getTargetObject(lotjuTmsStationMetadataClient);
        lotjuClient.setDestinationProvider(originalDestinationProvider);
    }

    @Test
    public void testUpdateTmsStations() {
        lotjuLAMMetatiedotServiceMock.initStateAndService();

        // Update TMS stations to initial state (3 non obsolete stations and 1 obsolete)
        tmsSensorUpdater.updateTmsSensors();
        tmsStationUpdater.updateTmsStations();
        tmsStationsSensorsUpdater.updateTmsStationsSensors();
//        tmsStationsSensorsUpdater.updateTmsStationsSensorConstants();
//        tmsStationsSensorsUpdater.updateTmsStationsSensorConstantsValues();

        final TmsStationFeatureCollection allInitial =
                tmsStationService.findAllPublishableTmsStationsAsFeatureCollection(false, ACTIVE);
        final TmsRoadStationsSensorsMetadata allSensorsInitial =
            roadStationSensorService.findTmsRoadStationsSensorsMetadata(false);

        assertEquals(3, allInitial.getFeatures().size());


        // Now change lotju metadata and update tms stations (2 non obsolete stations and 2 obsolete)
        lotjuLAMMetatiedotServiceMock.setStateAfterChange(true);
        tmsSensorUpdater.updateTmsSensors();
        tmsStationUpdater.updateTmsStations();
        tmsStationsSensorsUpdater.updateTmsStationsSensors();
//        tmsStationsSensorsUpdater.updateTmsStationsSensorConstants();
//        tmsStationsSensorsUpdater.updateTmsStationsSensorConstantsValues();

        final TmsStationFeatureCollection allAfterChange =
                tmsStationService.findAllPublishableTmsStationsAsFeatureCollection(false, ACTIVE);
        final TmsRoadStationsSensorsMetadata allSensorsAfterChange =
            roadStationSensorService.findTmsRoadStationsSensorsMetadata(false);
        assertEquals(2, allAfterChange.getFeatures().size());

        assertNotNull(findWithLotjuId(allInitial, 1));
        assertNull(findWithLotjuId(allInitial, 2));
        assertNotNull(findWithLotjuId(allInitial, 310));
        assertNotNull(findWithLotjuId(allInitial, 581));

        assertNotNull(findWithLotjuId(allAfterChange, 1));
        assertNull(findWithLotjuId(allAfterChange, 2));
        assertNotNull(findWithLotjuId(allAfterChange, 310));
        assertNull(findWithLotjuId(allAfterChange, 581));

        assertEquals(CollectionStatus.GATHERING, findWithLotjuId(allInitial, 1).getProperties().getCollectionStatus());
        assertEquals(CollectionStatus.GATHERING, findWithLotjuId(allInitial, 310).getProperties().getCollectionStatus());
        assertEquals(CollectionStatus.REMOVED_TEMPORARILY, findWithLotjuId(allInitial, 581).getProperties().getCollectionStatus());

        assertEquals(CollectionStatus.GATHERING, findWithLotjuId(allAfterChange, 1).getProperties().getCollectionStatus());
        assertEquals(CollectionStatus.GATHERING, findWithLotjuId(allAfterChange, 310).getProperties().getCollectionStatus());

        final TmsStationFeature before = findWithLotjuId(allInitial, 310);
        final TmsStationFeature after = findWithLotjuId(allAfterChange, 310);

        assertEquals("vt5_Iisalmi", before.getProperties().getName());
        assertEquals("vt5_Iidensalmi", after.getProperties().getName());

        // For conversions https://www.retkikartta.fi/
        assertEquals(512504.0, before.getProperties().getLongitudeETRS89(), 0.001);
        assertEquals(522504.0, after.getProperties().getLongitudeETRS89(), 0.001);
        assertEquals(7048771.0, before.getProperties().getLatitudeETRS89(), 0.001);
        assertEquals(7148771.0, after.getProperties().getLatitudeETRS89(), 0.001);
        assertEquals(0.0, before.getProperties().getAltitudeETRS89(), 0.001);
        assertEquals(1.0, after.getProperties().getAltitudeETRS89(), 0.001);

        assertEquals(27.25177, before.getGeometry().getLongitude(), 0.00005);
        assertEquals(27.46787, after.getGeometry().getLongitude(), 0.00005);

        assertEquals(63.56682, before.getGeometry().getLatitude(), 0.00005);
        assertEquals(64.46370, after.getGeometry().getLatitude(), 0.00005);

        assertEquals(0.0, before.getGeometry().getAltitude(), 0.00005);
        assertEquals(1.0, after.getGeometry().getAltitude(), 0.00005);

        assertEquals((Integer) 4750, before.getProperties().getRoadAddress().getDistanceFromRoadSectionStart());
        assertEquals((Integer) 4751, after.getProperties().getRoadAddress().getDistanceFromRoadSectionStart());

        assertEquals((Integer) 300, before.getProperties().getCollectionInterval());
        assertEquals((Integer) 301, after.getProperties().getCollectionInterval());

        assertEquals("Pohjois-Savo", before.getProperties().getProvince());
        assertEquals("Pohjois-Savvoo", after.getProperties().getProvince());

        assertEquals("Tie 5 Iisalmi", before.getProperties().getNames().get("fi"));
        assertEquals("Tie 5 Idensalmi", after.getProperties().getNames().get("fi"));

        assertEquals("Väg 5 Idensalmi", before.getProperties().getNames().get("sv"));
        assertEquals("Väg 5 Idensalmi", after.getProperties().getNames().get("sv"));

        assertEquals("Road 5 Iisalmi", before.getProperties().getNames().get("en"));
        assertEquals("Road 5 Idensalmi", after.getProperties().getNames().get("en"));

        assertEquals("Kajaani", before.getProperties().getDirection1Municipality());
        assertEquals("Kajaaniin", after.getProperties().getDirection1Municipality());

        assertEquals("Kuopio", before.getProperties().getDirection2Municipality());
        assertEquals("Kuopioon", after.getProperties().getDirection2Municipality());

        final TmsStationFeature before1 = findWithLotjuId(allInitial, 1);
        final TmsStationFeature after1 = findWithLotjuId(allAfterChange, 1);

        List<Long> sensorsInitial = before1.getProperties().getStationSensors();
        List<Long> sensorsAfter = after1.getProperties().getStationSensors();

        log.info("sensorsInitial={}", sensorsInitial);
        log.info("sensorsAfter={}", sensorsAfter);

        assertTrue(sensorsInitial.contains(5116L));
        assertTrue(sensorsInitial.contains(5119L));
        assertTrue(sensorsInitial.contains(5122L));
        assertFalse(sensorsInitial.contains(5125L));

        assertTrue(sensorsAfter.contains(5116L));
        assertFalse(sensorsAfter.contains(5119L)); // public false
        assertFalse(sensorsAfter.contains(5122L));
        assertTrue(sensorsAfter.contains(5125L));

        TmsRoadStationSensorDto initialSensor = allSensorsInitial.getRoadStationSensors().stream().filter(x -> x.getNaturalId() == 5116L).findFirst().orElse(null);
        TmsRoadStationSensorDto afterChangeSensor = allSensorsAfterChange.getRoadStationSensors().stream().filter(x -> x.getNaturalId() == 5116L).findFirst().orElse(null);
        assertNull(initialSensor.getDirection());
        assertNull(initialSensor.getLane());
        assertNull(initialSensor.getVehicleClass());

        assertEquals(1, afterChangeSensor.getDirection().intValue());
        assertEquals(2, afterChangeSensor.getLane().intValue());
        assertEquals(VehicleClass.TRUCK, afterChangeSensor.getVehicleClass());
    }

    private TmsStationFeature findWithLotjuId(final TmsStationFeatureCollection collection, final long lotjuId) {
        final Optional<TmsStationFeature> initial =
                collection.getFeatures().stream()
                        .filter(x -> x.getProperties().getLotjuId() == lotjuId)
                        .findFirst();
        return initial.orElse(null);
    }
}
