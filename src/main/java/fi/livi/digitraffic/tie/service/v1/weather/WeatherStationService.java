package fi.livi.digitraffic.tie.service.v1.weather;

import static fi.livi.digitraffic.tie.helper.DateHelper.getGreatestAtUtc;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.tie.converter.feature.WeatherStationMetadata2FeatureConverter;
import fi.livi.digitraffic.tie.dao.v1.WeatherStationRepository;
import fi.livi.digitraffic.tie.external.lotju.metadata.tiesaa.TiesaaAsemaVO;
import fi.livi.digitraffic.tie.helper.ToStringHelper;
import fi.livi.digitraffic.tie.metadata.geojson.weather.WeatherStationFeatureCollection;
import fi.livi.digitraffic.tie.model.DataType;
import fi.livi.digitraffic.tie.model.WeatherStationType;
import fi.livi.digitraffic.tie.model.v1.RoadStation;
import fi.livi.digitraffic.tie.model.v1.WeatherStation;
import fi.livi.digitraffic.tie.service.DataStatusService;
import fi.livi.digitraffic.tie.service.ObjectNotFoundException;
import fi.livi.digitraffic.tie.service.UpdateStatus;

@Service
public class WeatherStationService extends AbstractWeatherStationAttributeUpdater {

    private static final Logger log = LoggerFactory.getLogger(WeatherStationService.class);
    private final WeatherStationRepository weatherStationRepository;
    private final DataStatusService dataStatusService;
    private final WeatherStationMetadata2FeatureConverter weatherStationMetadata2FeatureConverter;

    @Autowired
    public WeatherStationService(final WeatherStationRepository weatherStationRepository,
                                 final DataStatusService dataStatusService,
                                 final WeatherStationMetadata2FeatureConverter weatherStationMetadata2FeatureConverter) {
        this.weatherStationRepository = weatherStationRepository;
        this.dataStatusService = dataStatusService;
        this.weatherStationMetadata2FeatureConverter = weatherStationMetadata2FeatureConverter;
    }

    @Transactional(readOnly = true)
    public Map<Long, WeatherStation> findAllWeatherStationsMappedByLotjuId() {
        final List<WeatherStation> all = weatherStationRepository.findAll();
        return all.parallelStream().collect(Collectors.toMap(WeatherStation::getLotjuId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<Long, WeatherStation> findAllPublishableWeatherStationsMappedByLotjuId() {
        final List<WeatherStation> all = findAllPublishableWeatherStations();
        return all.parallelStream().collect(Collectors.toMap(WeatherStation::getLotjuId, p -> p));
    }

    @Transactional(readOnly = true)
    public WeatherStationFeatureCollection findAllPublishableWeatherStationAsFeatureCollection(final boolean onlyUpdateInfo) {
        return weatherStationMetadata2FeatureConverter.convert(
                !onlyUpdateInfo ?
                    weatherStationRepository.findByRoadStationPublishableIsTrueOrderByRoadStation_NaturalId() :
                    Collections.emptyList(),
                getMetadataLastUpdated(),
                getMetadataLastChecked());
    }

    @Transactional(readOnly = true)
    public List<WeatherStation> findAllPublishableWeatherStations() {
        return weatherStationRepository.findByRoadStationPublishableIsTrueOrderByRoadStation_NaturalId();
    }

    @Transactional(readOnly = true)
    public WeatherStation findWeatherStationByLotjuId(final long tsaLotjuId) {
        return weatherStationRepository.findByLotjuId(tsaLotjuId);
    }

    @Transactional
    public UpdateStatus updateOrInsertWeatherStation(final TiesaaAsemaVO tiesaaAsema) {
        final WeatherStation rws = findWeatherStationByLotjuId(tiesaaAsema.getId());

        try {
            if (rws != null) {
                final int hash = HashCodeBuilder.reflectionHashCode(rws);
                final String before = ToStringHelper.toStringFull(rws);

                if (updateWeatherStationAttributes(tiesaaAsema, rws) ||
                    hash != HashCodeBuilder.reflectionHashCode(rws)) {
                    log.info("method=updateOrInsertWeatherStation \n{} -> \n{}", before, ToStringHelper.toStringFull(rws));
                    return UpdateStatus.UPDATED;
                }
                return UpdateStatus.NOT_UPDATED;
            } else {
                final WeatherStation newRws = new WeatherStation();
                newRws.setRoadStation(RoadStation.createWeatherStation());
                updateWeatherStationAttributes(tiesaaAsema, newRws);
                weatherStationRepository.save(newRws);
                log.info("method=updateOrInsertWeatherStation Created new {}", newRws);
                return UpdateStatus.INSERTED;
            }
        } finally {
            // Needed for tests to work :(
            weatherStationRepository.flush();
        }
    }

    @Override
    @Transactional
    public boolean updateStationToObsoleteWithLotjuId(final long lotjuId) {
        final WeatherStation station = weatherStationRepository.findByLotjuId(lotjuId);
        if (station != null) {
            return station.makeObsolete();
        }
        return false;
    }

    private static boolean updateWeatherStationAttributes(final TiesaaAsemaVO from,
                                                          final WeatherStation to) {
        final int hash = HashCodeBuilder.reflectionHashCode(to);

        to.setLotjuId(from.getId());
        to.setMaster(from.isMaster() != null ? from.isMaster() : true);
        to.setWeatherStationType(WeatherStationType.fromTiesaaAsemaTyyppi(from.getTyyppi()));

        // Update RoadStation
        return updateRoadStationAttributes(from, to.getRoadStation()) ||
            HashCodeBuilder.reflectionHashCode(to) != hash;
    }

    private ZonedDateTime getMetadataLastUpdated() {
        final ZonedDateTime sensorsUpdated = dataStatusService.findDataUpdatedTime(DataType.WEATHER_STATION_SENSOR_METADATA);
        final ZonedDateTime stationsUpdated = dataStatusService.findDataUpdatedTime(DataType.WEATHER_STATION_METADATA);
        return getGreatestAtUtc(sensorsUpdated, stationsUpdated);
    }

    private ZonedDateTime getMetadataLastChecked() {
        final ZonedDateTime sensorsUpdated = dataStatusService.findDataUpdatedTime(DataType.WEATHER_STATION_SENSOR_METADATA_CHECK);
        final ZonedDateTime stationsUpdated = dataStatusService.findDataUpdatedTime(DataType.WEATHER_STATION_METADATA_CHECK);
        return getGreatestAtUtc(sensorsUpdated, stationsUpdated);
    }

    @Transactional(readOnly = true)
    public WeatherStation findPublishableWeatherStationByRoadStationNaturalId(long roadStationNaturalId) {
        final WeatherStation entity = weatherStationRepository.findByRoadStation_NaturalIdAndRoadStationPublishableIsTrue(roadStationNaturalId);

        if (entity == null) {
            throw new ObjectNotFoundException(WeatherStation.class, roadStationNaturalId);
        }

        return entity;
    }

}
