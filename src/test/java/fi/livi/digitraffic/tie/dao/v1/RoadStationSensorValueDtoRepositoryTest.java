package fi.livi.digitraffic.tie.dao.v1;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.digitraffic.tie.AbstractJpaTest;
import fi.livi.digitraffic.tie.model.RoadStationType;

public class RoadStationSensorValueDtoRepositoryTest extends AbstractJpaTest {

    @Autowired
    private RoadStationSensorValueDtoRepository roadStationSensorValueDtoRepository;

    @Test
    public void findAllPublicPublishableRoadStationSensorValues() {
        roadStationSensorValueDtoRepository.findAllPublicPublishableRoadStationSensorValues(RoadStationType.TMS_STATION, 10);
    }

    @Test
    public void findAllPublicPublishableRoadStationSensorValuesWithNaturalId() {
        roadStationSensorValueDtoRepository.findAllPublicPublishableRoadStationSensorValues(1L, RoadStationType.TMS_STATION,10);
    }

    @Test
    public void findAllPublicPublishableRoadStationSensorValuesUpdatedAfter() {
        roadStationSensorValueDtoRepository.findAllPublicPublishableRoadStationSensorValuesUpdatedAfter(RoadStationType.TMS_STATION, Instant.now());
    }

}
