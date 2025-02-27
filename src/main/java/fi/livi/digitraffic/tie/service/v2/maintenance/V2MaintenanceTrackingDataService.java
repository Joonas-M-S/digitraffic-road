package fi.livi.digitraffic.tie.service.v2.maintenance;

import static fi.livi.digitraffic.tie.helper.DateHelper.toZonedDateTimeAtUtc;
import static fi.livi.digitraffic.tie.model.DataType.MAINTENANCE_TRACKING_DATA_CHECKED;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import fi.livi.digitraffic.tie.dao.v2.V2MaintenanceTrackingRepository;
import fi.livi.digitraffic.tie.dao.v3.V3MaintenanceTrackingObservationDataRepository;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingFeature;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingFeatureCollection;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingLatestFeature;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingLatestFeatureCollection;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingLatestProperties;
import fi.livi.digitraffic.tie.dto.maintenance.old.MaintenanceTrackingProperties;
import fi.livi.digitraffic.tie.helper.DateHelper;
import fi.livi.digitraffic.tie.helper.PostgisGeometryUtils;
import fi.livi.digitraffic.tie.metadata.geojson.Geometry;
import fi.livi.digitraffic.tie.model.v2.maintenance.MaintenanceTrackingDto;
import fi.livi.digitraffic.tie.model.v2.maintenance.MaintenanceTrackingForMqttV2;
import fi.livi.digitraffic.tie.model.v2.maintenance.MaintenanceTrackingTask;
import fi.livi.digitraffic.tie.service.DataStatusService;
import fi.livi.digitraffic.tie.service.ObjectNotFoundException;

/**
 * This service returns Harja and municipality tracking data for public use
 *
 * @see fi.livi.digitraffic.tie.service.v3.maintenance.V3MaintenanceTrackingUpdateService
 * @see <a href="https://github.com/finnishtransportagency/harja">https://github.com/finnishtransportagency/harja</a>
 */
@Service
public class V2MaintenanceTrackingDataService {

    private static final Logger log = LoggerFactory.getLogger(V2MaintenanceTrackingDataService.class);
    private final V2MaintenanceTrackingRepository v2MaintenanceTrackingRepository;
    private final V3MaintenanceTrackingObservationDataRepository v3MaintenanceTrackingObservationDataRepository;
    private final DataStatusService dataStatusService;

    private final ObjectMapper objectMapper;
    private static ObjectReader geometryReader;

    @Autowired
    public V2MaintenanceTrackingDataService(final V2MaintenanceTrackingRepository v2MaintenanceTrackingRepository,
                                            final V3MaintenanceTrackingObservationDataRepository v3MaintenanceTrackingObservationDataRepository,
                                            final DataStatusService dataStatusService,
                                            final ObjectMapper objectMapper) {
        this.v2MaintenanceTrackingRepository = v2MaintenanceTrackingRepository;
        this.v3MaintenanceTrackingObservationDataRepository = v3MaintenanceTrackingObservationDataRepository;
        this.dataStatusService = dataStatusService;
        this.objectMapper = objectMapper;
        geometryReader = objectMapper.readerFor(Geometry.class);
    }

    @Transactional(readOnly = true)
    public MaintenanceTrackingLatestFeatureCollection findLatestMaintenanceTrackings(final Instant endTimefrom, final Instant endTimeto,
                                                                                     final double xMin, final double yMin,
                                                                                     final double xMax, final double yMax,
                                                                                     final List<MaintenanceTrackingTask> taskIds,
                                                                                     final List<String> domains) {
        final List<String> realDomains = convertToRealDomainNames(domains);
        final ZonedDateTime lastUpdated = toZonedDateTimeAtUtc(v2MaintenanceTrackingRepository.findLastUpdatedForDomain(realDomains));
        final ZonedDateTime lastChecked = toZonedDateTimeAtUtc(dataStatusService.findDataUpdatedTime(MAINTENANCE_TRACKING_DATA_CHECKED, realDomains));

        final Polygon area = PostgisGeometryUtils.createSquarePolygonFromMinMax(xMin, xMax, yMin, yMax);

        final StopWatch start = StopWatch.createStarted();
        final List<MaintenanceTrackingDto> found =
            v2MaintenanceTrackingRepository.findLatestByAgeAndBoundingBoxAndTasks(
                toZonedDateTimeAtUtc(endTimefrom),
                toZonedDateTimeAtUtc(endTimeto),
                area,
                convertTasksToStringArrayOrNull(taskIds),
                realDomains);

        log.info("method=findLatestMaintenanceTrackings with params xMin {}, xMax {}, yMin {}, yMax {} fromTime={} toTime={} foundCount={} tookMs={}",
            xMin, xMax, yMin, yMax, toZonedDateTimeAtUtc(endTimefrom), toZonedDateTimeAtUtc(endTimeto), found.size(), start.getTime());

        final List<MaintenanceTrackingLatestFeature> features = convertToTrackingLatestFeatures(found);
        return new MaintenanceTrackingLatestFeatureCollection(lastUpdated, lastChecked, features);
    }

    @Transactional(readOnly = true)
    public MaintenanceTrackingFeatureCollection findMaintenanceTrackings(final Instant endTimeFrom, final Instant endTimeBefore,
                                                                         final Instant createdAfter, final Instant createdBefore,
                                                                         final double xMin, final double yMin,
                                                                         final double xMax, final double yMax,
                                                                         final List<MaintenanceTrackingTask> taskIds,
                                                                         final List<String> domains) {
        final List<String> realDomains = convertToRealDomainNames(domains);
        final Instant lastUpdated = v2MaintenanceTrackingRepository.findLastUpdatedForDomain(realDomains);
        final Instant lastChecked = dataStatusService.findDataUpdatedTime(MAINTENANCE_TRACKING_DATA_CHECKED, realDomains);

        final Polygon area = PostgisGeometryUtils.createSquarePolygonFromMinMax(xMin, xMax, yMin, yMax);

        final StopWatch start = StopWatch.createStarted();
        final List<MaintenanceTrackingDto> found =
            v2MaintenanceTrackingRepository.findByAgeAndBoundingBoxAndTasks(
                toZonedDateTimeAtUtc(endTimeFrom), toZonedDateTimeAtUtc(endTimeBefore),
                toZonedDateTimeAtUtc(createdAfter), toZonedDateTimeAtUtc(createdBefore),
                area, convertTasksToStringArrayOrNull(taskIds), realDomains);

        log.info("method=findMaintenanceTrackingsV1 with params xMin {}, xMax {}, yMin {}, yMax {} endTimeFrom {} endTimeBefore {} createdAfter {} createdBefore {} domains {} foundCount {} tookMs={}",
            xMin, xMax, yMin, yMax, endTimeFrom, endTimeBefore, createdAfter, createdBefore, realDomains, found.size(), start.getTime());

        final StopWatch startConvert = StopWatch.createStarted();
        final List<MaintenanceTrackingFeature> features = convertToTrackingFeatures(found);
        log.info("method=findMaintenanceTrackingsV1-convert with params xMin {}, xMax {}, yMin {}, yMax {} endTimeFrom {} endTimeBefore {} createdAfter {} createdBefore {} domains {} foundCount {} tookMs={}",
            xMin, xMax, yMin, yMax, endTimeFrom, endTimeBefore, createdAfter, createdBefore, realDomains, found.size(), startConvert.getTime());

        return new MaintenanceTrackingFeatureCollection(lastUpdated, lastChecked, features);
    }

    @Transactional(readOnly = true)
    public MaintenanceTrackingFeatureCollection findMaintenanceTrackings(final Instant endTimeFrom, final Instant endTimeTo,
                                                                         final double xMin, final double yMin,
                                                                         final double xMax, final double yMax,
                                                                         final List<MaintenanceTrackingTask> taskIds,
                                                                         final List<String> domains) {
        return findMaintenanceTrackings(endTimeFrom, DateHelper.appendMillis(endTimeTo, 1), null, null, xMin, yMin, xMax, yMax, taskIds, domains);
    }

    /**
     * Converts given domain name parameters to real domain names in db.
     * Rules for parameters:
     * - null or empty => defaults to state-roads
     * - generic all domains OR generic all municipalities + state-roads => all possible domains available
     * - generic all municipalities => all municipality domains available
     * - other => parameter as it is
     *
     * @param domainNameParameters parameters to convert to real domain names
     * @return Actual real domain names
     */
    private List<String> convertToRealDomainNames(final List<String> domainNameParameters) {
        if (CollectionUtils.isEmpty(domainNameParameters)) {
            // Without parameter default to STATE_ROADS_DOMAIN
            return Collections.singletonList(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN);
        } else if (domainNameParameters.contains(V2MaintenanceTrackingRepository.GENERIC_ALL_DOMAINS) ||
            (domainNameParameters.contains(V2MaintenanceTrackingRepository.GENERIC_MUNICIPALITY_DOMAINS) &&
             domainNameParameters.contains(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN)) ) {
            return getRealDomainNames();
        } else if (domainNameParameters.contains(V2MaintenanceTrackingRepository.GENERIC_MUNICIPALITY_DOMAINS)) {
            return getRealDomainNamesWithoutStateRoadsDomain();
        }
        return domainNameParameters;
    }

    private List<String> getRealDomainNames() {
        return v2MaintenanceTrackingRepository.getRealDomainNames();
    }

    private List<String> getRealDomainNamesWithoutStateRoadsDomain() {
        final List<String> all = getRealDomainNames();
        all.remove(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN);
        return all;
    }

    private List<String> convertTasksToStringArrayOrNull(final List<MaintenanceTrackingTask> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return taskIds.stream().filter(Objects::nonNull).map(Enum::name).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MaintenanceTrackingFeature getMaintenanceTrackingById(final long id) throws ObjectNotFoundException {
        final MaintenanceTrackingDto tracking = v2MaintenanceTrackingRepository.getDto(id);
        if (tracking != null) {
            return convertToTrackingFeature(tracking);
        }
        throw new ObjectNotFoundException("MaintenanceTracking", id);
    }

    @Transactional(readOnly = true)
    public List<JsonNode> findTrackingDataJsonsByTrackingId(final long trackingId) {
        return v3MaintenanceTrackingObservationDataRepository.findJsonsByTrackingId(trackingId).stream().map(j -> {
            try {
                return objectMapper.readTree(j);
            } catch (final JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTrackingForMqttV2> findTrackingsForMqttCreatedAfter(final Instant from) {
        return v2MaintenanceTrackingRepository.findTrackingsCreatedAfter(from);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTrackingLatestFeature> findTrackingsLatestPointsCreatedAfter(final Instant from) {
        return v2MaintenanceTrackingRepository.findTrackingsLatestPointsCreatedAfter(from).stream()
            .map(V2MaintenanceTrackingDataService::convertToTrackingLatestFeature)
            .collect(Collectors.toList());
    }

    private static List<MaintenanceTrackingFeature> convertToTrackingFeatures(final List<MaintenanceTrackingDto> trackings) {
        return trackings.parallelStream().map(V2MaintenanceTrackingDataService::convertToTrackingFeature).collect(Collectors.toList());
    }

    private static List<MaintenanceTrackingLatestFeature> convertToTrackingLatestFeatures(final List<MaintenanceTrackingDto> trackings) {
        return trackings.parallelStream().map(V2MaintenanceTrackingDataService::convertToTrackingLatestFeature).collect(Collectors.toList());
    }

    private static MaintenanceTrackingFeature convertToTrackingFeature(final MaintenanceTrackingDto tracking) {
        final Geometry<?> geometry = convertToGeoJSONGeometry(tracking, false);
        final MaintenanceTrackingProperties properties =
            new MaintenanceTrackingProperties(tracking.getId(),
                tracking.getPreviousId(),
                tracking.getWorkMachineId(),
                tracking.getSendingTime(),
                tracking.getStartTime(),
                tracking.getEndTime(),
                tracking.getCreated(),
                tracking.getTasks(), tracking.getDirection(),
                tracking.getDomain(),
                tracking.getSource());
        return new MaintenanceTrackingFeature(geometry, properties);
    }

    public static MaintenanceTrackingLatestFeature convertToTrackingLatestFeature(final MaintenanceTrackingDto tracking) {
        final Geometry<?> geometry = convertToGeoJSONGeometry(tracking, true);
        final MaintenanceTrackingLatestProperties properties =
            new MaintenanceTrackingLatestProperties(tracking.getId(),
                                                    tracking.getEndTime(),
                                                    tracking.getCreated(),
                                                    tracking.getTasks(), tracking.getDirection(),
                                                    tracking.getDomain(),
                                                    tracking.getSource());
        return new MaintenanceTrackingLatestFeature(geometry, properties);
    }

    /**
     * @param tracking that contains the geometry
     * @param latestPointGeometry if true then only the latest point will be returned as the geometry.
     * @return either Point or LineString geometry
     */
    private static Geometry<?> convertToGeoJSONGeometry(final MaintenanceTrackingDto tracking, boolean latestPointGeometry) {
        if (latestPointGeometry) {
            return readGeometry(tracking.getLastPointJson());
        }
        return readGeometry(tracking.getGeometryStringJson());
    }

    private static Geometry<?> readGeometry(final String json) {
        try {
            return geometryReader.readValue(json);
        } catch (final JsonProcessingException e) {
            log.error(String.format("Error while converting json geometry to GeoJson: %s", json), e);
            return null;
        }
    }
}
