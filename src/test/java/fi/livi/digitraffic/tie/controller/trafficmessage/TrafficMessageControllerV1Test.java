package fi.livi.digitraffic.tie.controller.trafficmessage;

import static fi.livi.digitraffic.tie.dto.trafficmessage.v1.SituationType.TRAFFIC_ANNOUNCEMENT;
import static fi.livi.digitraffic.tie.model.DataType.TRAFFIC_MESSAGES_REGION_GEOMETRY_DATA;
import static fi.livi.digitraffic.tie.service.TrafficMessageTestHelper.ImsJsonVersion;
import static fi.livi.digitraffic.tie.service.TrafficMessageTestHelper.ImsXmlVersion;
import static fi.livi.digitraffic.tie.service.TrafficMessageTestHelper.getSituationIdForSituationType;
import static fi.livi.digitraffic.tie.service.TrafficMessageTestHelper.getVersionTime;
import static fi.livi.digitraffic.tie.service.v2.datex2.RegionGeometryTestHelper.createRegionGeometryFeatureCollection;
import static fi.livi.digitraffic.tie.service.v2.datex2.RegionGeometryTestHelper.createRegionsInDescOrderMappedByLocationCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.xml.transform.StringSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import fi.livi.digitraffic.tie.AbstractRestWebTestWithRegionGeometryGitAndDataServiceMock;
import fi.livi.digitraffic.tie.TestUtils;
import fi.livi.digitraffic.tie.conf.LastModifiedAppenderControllerAdvice;
import fi.livi.digitraffic.tie.datex2.D2LogicalModel;
import fi.livi.digitraffic.tie.datex2.Situation;
import fi.livi.digitraffic.tie.datex2.SituationPublication;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.RoadWorkPhase;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.SituationType;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TimeAndDuration;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TrafficAnnouncement;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TrafficAnnouncementFeature;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TrafficAnnouncementFeatureCollection;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TrafficAnnouncementProperties;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.TrafficAnnouncementType;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.WeekdayTimePeriod;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.region.RegionGeometryFeature;
import fi.livi.digitraffic.tie.dto.trafficmessage.v1.region.RegionGeometryFeatureCollection;
import fi.livi.digitraffic.tie.helper.DateHelper;
import fi.livi.digitraffic.tie.model.v3.trafficannouncement.geojson.RegionGeometry;
import fi.livi.digitraffic.tie.service.DataStatusService;
import fi.livi.digitraffic.tie.service.TrafficMessageTestHelper;
import fi.livi.digitraffic.tie.service.trafficmessage.Datex2Helper;
import fi.livi.digitraffic.tie.service.trafficmessage.v1.RegionGeometryDataServiceV1;

public class TrafficMessageControllerV1Test extends AbstractRestWebTestWithRegionGeometryGitAndDataServiceMock {
    private static final Logger log = getLogger(TrafficMessageControllerV1Test.class);

    @Autowired
    @Qualifier("datex2Jaxb2Marshaller")
    private Jaxb2Marshaller datex2Jaxb2Marshaller;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrafficMessageTestHelper trafficMessageTestHelper;


    @Autowired
    private DataStatusService dataStatusService;

    @BeforeEach
    public void init() {
        final Map<Integer, List<RegionGeometry>> regionsInDescOrderMappedByLocationCode =
            createRegionsInDescOrderMappedByLocationCode(0, 3, 7, 14, 408, 5898);

        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(0).get(0));
        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(3).get(0));
        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(7).get(0));
        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(14).get(0));
        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(408).get(0));
        whenV3RegionGeometryDataServicGetAreaLocationRegionEffectiveOn(regionsInDescOrderMappedByLocationCode.get(5898).get(0));

        final RegionGeometryFeatureCollection featureCollectionWithGeometry =
            createRegionGeometryFeatureCollection(RegionGeometryDataServiceV1.convertToDtoList(regionsInDescOrderMappedByLocationCode, true));
        final RegionGeometryFeatureCollection featureCollectionWithoutGeometry =
            createRegionGeometryFeatureCollection(RegionGeometryDataServiceV1.convertToDtoList(regionsInDescOrderMappedByLocationCode, false));

        when(regionGeometryDataServiceV1.findAreaLocationRegions(eq(false), eq(true), isNull(), isNull())).thenReturn(featureCollectionWithGeometry);
        when(regionGeometryDataServiceV1.findAreaLocationRegions(eq(false), eq(false), isNull(), isNull())).thenReturn(featureCollectionWithoutGeometry);

        // Map return value for locationCode 3
        final RegionGeometryFeature feature3 =
            featureCollectionWithGeometry.getFeatures().stream().filter(f -> f.getProperties().locationCode.equals(3)).findFirst().orElseThrow();
        when(regionGeometryDataServiceV1.findAreaLocationRegions(eq(false), eq(true), isNull(), eq(3)))
            .thenReturn(createRegionGeometryFeatureCollection(Collections.singletonList(feature3)));
    }

    @AfterEach
    public void cleanDb() {
        TestUtils.truncateTrafficMessageData(entityManager);
    }

    /**
     * Test all Ims Xml Versions and all message types to be returned by the controller
     */
    @Test
    public void getJsonAndXmlCurrentlyActive() throws Exception {
        for (final ImsXmlVersion imsXmlVersion : ImsXmlVersion.values()) {
            for (final ImsJsonVersion imsJsonVersion : ImsJsonVersion.values()) {
                for(final SituationType situationType : SituationType.values()) {
                    trafficMessageTestHelper.cleanDb();
                    final ZonedDateTime start = DateHelper.getZonedDateTimeNowWithoutMillisAtUtc().minusHours(1);
                    final ZonedDateTime end = start.plusHours(2);
                    final Instant lastUpdated = DateHelper.roundToSeconds(getTransactionTimestamp());
                    trafficMessageTestHelper.initDataFromStaticImsResourceContent(imsXmlVersion, situationType.name(), imsJsonVersion, start, end);
                    log.info("getJsonAndXmlCurrentlyActive with imsXmlVersion={}, imsJsonVersion={} and situationType={}", imsXmlVersion, imsJsonVersion, situationType);
                    final String xml = getResponse(getTrafficMessageUrlWithType(false, 0, situationType), lastUpdated);
                    final String json = getResponse(getTrafficMessageUrlWithType(true, 0, situationType), lastUpdated);
                    assertIsValidDatex2Xml(xml);
                    assertTextIsValidJson(json);
                    assertTimesFormatMatchesIsoDateTimeWithZ(xml);
                    assertTimesFormatMatchesIsoDateTimeWithZ(json);
                    assertContentsMatch(xml, json, situationType, getSituationIdForSituationType(situationType.name()), start, end, imsJsonVersion);
                    assertTraficAnouncmentTypeUpperCase(json, situationType);
                }
            }
        }
    }

    /**
     * Test all Ims Xml Versions and all message types to be returned by the controller when they are inactive and inside inactive hours parameter
     */
    @Test
    public void getJsonAndXmlCurrentlyInactiveWithInactiveHours() throws Exception {
        for (final ImsXmlVersion imsXmlVersion : ImsXmlVersion.values()) {
            for (final ImsJsonVersion imsJsonVersion : ImsJsonVersion.values()) {
                for(final SituationType situationType : SituationType.values()) {
                    trafficMessageTestHelper.cleanDb();
                    final ZonedDateTime start = DateHelper.getZonedDateTimeNowWithoutMillisAtUtc().minusHours(3);
                    final ZonedDateTime end = start.plusHours(2);
                    final Instant lastUpdated = DateHelper.roundToSeconds(getTransactionTimestamp());
                    trafficMessageTestHelper.initDataFromStaticImsResourceContent(imsXmlVersion, situationType.name(), imsJsonVersion, start, end);
                    log.info("getJsonAndXmlCurrentlyActive with imsXmlVersion={}, imsJsonVersion={} and situationType={}", imsXmlVersion, imsJsonVersion, situationType);
                    final String xml = getResponse(getTrafficMessageUrlWithType(false, 2, situationType), lastUpdated);
                    final String json = getResponse(getTrafficMessageUrlWithType(true, 2, situationType), lastUpdated);
                    assertIsValidDatex2Xml(xml);
                    assertTextIsValidJson(json);
                    assertTimesFormatMatchesIsoDateTimeWithZ(xml);
                    assertTimesFormatMatchesIsoDateTimeWithZ(json);
                    assertContentsMatch(xml, json, situationType, getSituationIdForSituationType(situationType.name()), start, end, imsJsonVersion);
                    assertTraficAnouncmentTypeUpperCase(json, situationType);
                }
            }
        }
    }

    /**
     * Tests all Ims Xml Versions and all message types to not be returned by the controller when they are passive.
     */
    @Test
    public void getJsonAndXmlCurrentlyInactive() throws Exception {
        for (final ImsXmlVersion imsXmlVersion : ImsXmlVersion.values()) {
            for (final ImsJsonVersion imsJsonVersion : ImsJsonVersion.values()) {
                for(final SituationType situationType : SituationType.values()) {
                    trafficMessageTestHelper.cleanDb();
                    final ZonedDateTime start = DateHelper.getZonedDateTimeNowWithoutMillisAtUtc().minusHours(3);
                    final ZonedDateTime end = start.plusHours(2);
                    trafficMessageTestHelper.initDataFromStaticImsResourceContent(imsXmlVersion, situationType.name(), imsJsonVersion, start, end);
                    log.info("getJsonAndXmlCurrentlyPassive with imsXmlVersion={}, imsJsonVersion={} and situationType={}", imsXmlVersion, imsJsonVersion, situationType);
                    final String xml = getResponse(getTrafficMessageUrlWithType(false, 0, situationType));
                    final String json = getResponse(getTrafficMessageUrlWithType(true, 0, situationType));
                    assertIsValidDatex2Xml(xml);
                    assertTextIsValidJson(json);
                    assertEmptyD2Situations(xml);
                    assertEmptyJsonSituations(json);
                }
            }
        }
    }

    @Test
    public void getRegionGeometry() throws Exception {
        final Instant lastUpdated = DateHelper.withoutMillis(dataStatusService.findDataUpdatedInstant(TRAFFIC_MESSAGES_REGION_GEOMETRY_DATA));
        final String json = getResponse(getRegionGeometryUrl(true), lastUpdated);
        final RegionGeometryFeatureCollection result = parseRegionGeometryFeatureCollectionJson(json);
        assertEquals(6, result.getFeatures().size());
        result.getFeatures().forEach(f -> assertNotNull(f.getGeometry()));
    }

    @Test
    public void getRegionGeometryWithoutGeometry() throws Exception {
        final Instant lastUpdated = DateHelper.withoutMillis(dataStatusService.findDataUpdatedInstant(TRAFFIC_MESSAGES_REGION_GEOMETRY_DATA));
        final String json = getResponse(getRegionGeometryUrl(false), lastUpdated);
        final RegionGeometryFeatureCollection result = parseRegionGeometryFeatureCollectionJson(json);
        assertEquals(6, result.getFeatures().size());
        result.getFeatures().forEach(f -> assertNull(f.getGeometry()));
    }

    @Test
    public void getRegionGeometryWithId() throws Exception {
        final Instant lastUpdated = DateHelper.withoutMillis(dataStatusService.findDataUpdatedInstant(TRAFFIC_MESSAGES_REGION_GEOMETRY_DATA));
        final String json = getResponse(getRegionGeometryUrl(true, 3), lastUpdated);
        final RegionGeometryFeatureCollection result = parseRegionGeometryFeatureCollectionJson(json);
        assertEquals(1, result.getFeatures().size());
        assertNotNull(result.getFeatures().get(0).getGeometry());
        assertEquals(3, result.getFeatures().get(0).getProperties().locationCode);
    }

    private void assertEmptyD2Situations(String xml) {
        final D2LogicalModel d2 = parseD2LogicalModel(xml);
        assertNull(d2.getPayloadPublication());
    }

    private void assertEmptyJsonSituations(final String simpleJson) throws JsonProcessingException {
        final TrafficAnnouncementFeatureCollection fc = parseSimpleJson(simpleJson);
        assertTrue(fc.getFeatures().isEmpty());
    }

    private TrafficAnnouncementFeatureCollection parseSimpleJson(final String simpleJson) throws JsonProcessingException {
        final ObjectReader r = objectMapper.readerFor(TrafficAnnouncementFeatureCollection.class);
        return r.readValue(simpleJson);
    }

    private RegionGeometryFeatureCollection parseRegionGeometryFeatureCollectionJson(final String regionGeometryFeatureCollection) throws JsonProcessingException {
        final ObjectReader r = objectMapper.readerFor(RegionGeometryFeatureCollection.class);
        return r.readValue(regionGeometryFeatureCollection);
    }

    private D2LogicalModel parseD2LogicalModel(final String d2xml) {
        return (D2LogicalModel) datex2Jaxb2Marshaller.unmarshal(new StringSource(d2xml));
    }

    private void assertContentsMatch(final String d2xml, final String simpleJsonFeatureCollection, final SituationType situationType,
                                     final String situationId,
                                     final ZonedDateTime start, final ZonedDateTime end,
                                     final ImsJsonVersion imsJsonVersion)
        throws JsonProcessingException {
        final D2LogicalModel d2 = parseD2LogicalModel(d2xml);

        final TrafficAnnouncementFeatureCollection fc = parseSimpleJson(simpleJsonFeatureCollection);
        final TrafficAnnouncementFeature feature = fc.getFeatures().get(0);

        final SituationPublication sp = Datex2Helper.getSituationPublication(d2);
        final Situation situation = sp.getSituations().get(0);
        final TrafficAnnouncementProperties jsonProperties = feature.getProperties();

        assertEquals(situationId, situation.getId());
        assertEquals(situationId, jsonProperties.situationId);

        final TimeAndDuration jsonTimeAndDuration = jsonProperties.announcements.get(0).timeAndDuration;

        assertEquals(start.toInstant(), situation.getSituationRecords().get(0).getValidity().getValidityTimeSpecification().getOverallStartTime());
        assertEquals(start.toInstant(), jsonTimeAndDuration.startTime.toInstant());

        final Instant versionTime = getVersionTime(start, imsJsonVersion).toInstant();
        assertEquals(versionTime, situation.getSituationRecords().get(0).getSituationRecordVersionTime());
        assertEquals(versionTime, jsonProperties.releaseTime.toInstant());

        assertEquals(end.toInstant(), situation.getSituationRecords().get(0).getValidity().getValidityTimeSpecification().getOverallEndTime());
        assertEquals(end.toInstant(), jsonTimeAndDuration.endTime.toInstant());

        final String commentXml = situation.getSituationRecords().get(0).getGeneralPublicComments().get(0).getComment().getValues().getValues().stream()
            .filter(c -> c.getLang().equals("fi")).findFirst().orElseThrow().getValue();

        assertEquals(situationType.name(), jsonProperties.getSituationType().name());
        if (situationType == TRAFFIC_ANNOUNCEMENT) {
            assertTrue(Sets.newHashSet(TrafficAnnouncementType.values()).contains(jsonProperties.getTrafficAnnouncementType()));
        }

        final TrafficAnnouncement announcement = jsonProperties.announcements.get(0);
        assertTrue(commentXml.contains(announcement.title.trim()));

        if (imsJsonVersion.version >= ImsJsonVersion.V0_2_5.version && situationType.equals(SituationType.ROAD_WORK)) {
            final RoadWorkPhase rwp =
                feature.getProperties().announcements.get(0).roadWorkPhases.get(0);
            assertEquals(WeekdayTimePeriod.Weekday.MONDAY, rwp.workingHours.get(0).weekday);
            assertEquals(LocalTime.parse("09:30:00.000"), rwp.workingHours.get(0).startTime);
            assertEquals(LocalTime.parse("15:00:00.000"), rwp.workingHours.get(0).endTime);
        }
        if (imsJsonVersion.version >= ImsJsonVersion.V0_2_17.version && situationType.equals(SituationType.ROAD_WORK)) {
            final RoadWorkPhase rwp =
                feature.getProperties().announcements.get(0).roadWorkPhases.get(0);
            assertEquals(WeekdayTimePeriod.Weekday.TUESDAY, rwp.slowTrafficTimes.get(0).weekday);
            assertEquals(WeekdayTimePeriod.Weekday.WEDNESDAY, rwp.queuingTrafficTimes.get(0).weekday);
            assertEquals(LocalTime.parse("10:30:00.000"), rwp.slowTrafficTimes.get(0).startTime);
            assertEquals(LocalTime.parse("16:00:00.000"), rwp.slowTrafficTimes.get(0).endTime);
            assertEquals(LocalTime.parse("11:30:00.000"), rwp.queuingTrafficTimes.get(0).startTime);
            assertEquals(LocalTime.parse("17:00:00.000"), rwp.queuingTrafficTimes.get(0).endTime);
        }
    }

    private void assertIsValidDatex2Xml(final String xml) {
        try {
            datex2Jaxb2Marshaller.unmarshal(new StringSource(xml));
        } catch (final XmlMappingException e) {
            throw new IllegalArgumentException("Not XML: " + xml, e);
        }
    }

    private void assertTextIsValidJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Not JSON: " + json, e);
        }
    }

    private static String getTrafficMessageUrlWithType(final boolean json, final int inactiveHours, final SituationType situationType) {
        return TrafficMessageControllerV1.API_TRAFFIC_MESSAGE_V1_MESSAGES +
               (json ? "" : TrafficMessageControllerV1.DATEX2) +
               "?inactiveHours=" + inactiveHours + "&situationType=" + situationType.name();
    }

    private static String getRegionGeometryUrl(final boolean includeGeometry) {
        return TrafficMessageControllerV1.API_TRAFFIC_MESSAGE_V1 + TrafficMessageControllerV1.AREA_GEOMETRIES +
            "?lastUpdated=false&includeGeometry=" + includeGeometry;
    }

    private static String getRegionGeometryUrl(final boolean includeGeometry, int regionId) {
        return TrafficMessageControllerV1.API_TRAFFIC_MESSAGE_V1 + TrafficMessageControllerV1.AREA_GEOMETRIES + "/" + regionId +
            "?lastUpdated=false&includeGeometry=" + includeGeometry;
    }

    private String getResponse(final String url) throws Exception {
        return getResponse(url, null);
    }
    private String getResponse(final String url, final Instant lastUpdated) throws Exception {
        final MockHttpServletRequestBuilder get = MockMvcRequestBuilders.get(url);

        if (url.contains("datex2")) {
            get.contentType(MediaType.APPLICATION_XML);
        } else {
            get.contentType(MediaType.APPLICATION_JSON);
        }
        final ResultActions result = mockMvc.perform(get);
        if (lastUpdated != null) {
            result.andExpect(header().exists(LastModifiedAppenderControllerAdvice.LAST_MODIFIED_HEADER))
                  .andExpect(header().dateValue(LastModifiedAppenderControllerAdvice.LAST_MODIFIED_HEADER, lastUpdated.toEpochMilli()));
        }
        return result.andReturn().getResponse().getContentAsString();
    }

    private void assertTraficAnouncmentTypeUpperCase(final String json, final SituationType situationType) {
        if (situationType.equals(TRAFFIC_ANNOUNCEMENT)) {
            final String trafficAnnouncementType =
                StringUtils.substringBefore(
                    StringUtils.substringAfter(
                        StringUtils.substringAfter(
                            StringUtils.substringAfter(json, "trafficAnnouncementType"), ":"), "\""), "\"");
            final Set<String> values = Arrays.stream(TrafficAnnouncementType.values()).map(Enum::name).collect(Collectors.toSet());
            assertTrue(values.contains(trafficAnnouncementType));
            assertTrue(StringUtils.isAllUpperCase(trafficAnnouncementType.replace("_", "")));
        }
    }
}
