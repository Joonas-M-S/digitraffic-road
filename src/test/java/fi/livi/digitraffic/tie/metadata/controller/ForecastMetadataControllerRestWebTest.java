package fi.livi.digitraffic.tie.metadata.controller;

import static fi.livi.digitraffic.tie.controller.ApiPaths.API_METADATA_PART_PATH;
import static fi.livi.digitraffic.tie.controller.ApiPaths.API_V1_BASE_PATH;
import static fi.livi.digitraffic.tie.controller.ApiPaths.FORECAST_SECTIONS_PATH;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import fi.livi.digitraffic.tie.AbstractRestWebTest;
import fi.livi.digitraffic.tie.dao.v1.forecast.ForecastSectionRepository;
import fi.livi.digitraffic.tie.service.DataStatusService;
import fi.livi.digitraffic.tie.service.RestTemplateGzipService;
import fi.livi.digitraffic.tie.service.v1.forecastsection.ForecastSectionClient;
import fi.livi.digitraffic.tie.service.v1.forecastsection.ForecastSectionTestHelper;
import fi.livi.digitraffic.tie.service.v1.forecastsection.ForecastSectionV1MetadataUpdater;

public class ForecastMetadataControllerRestWebTest extends AbstractRestWebTest {

    @Autowired
    private ForecastSectionRepository forecastSectionRepository;

    @Autowired
    private DataStatusService dataStatusService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @BeforeEach
    public void initData() throws IOException {
        if (!isBeanRegistered(RestTemplateGzipService.class)) {
            final RestTemplateGzipService restTemplateGzipService = beanFactory.createBean(RestTemplateGzipService.class);
            beanFactory.registerSingleton(restTemplateGzipService.getClass().getCanonicalName(), restTemplateGzipService);
        }
        final ForecastSectionTestHelper forecastSectionTestHelper =
            isBeanRegistered(ForecastSectionTestHelper.class) ?
                beanFactory.getBean(ForecastSectionTestHelper.class) :
                beanFactory.createBean(ForecastSectionTestHelper.class);

        final ForecastSectionClient forecastSectionClient = forecastSectionTestHelper.createForecastSectionClient();

        final ForecastSectionV1MetadataUpdater forecastSectionMetadataUpdater =
            new ForecastSectionV1MetadataUpdater(forecastSectionClient, forecastSectionRepository, dataStatusService);
        final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        forecastSectionTestHelper.serverExpectMetadata(server,1);

        forecastSectionMetadataUpdater.updateForecastSectionV1Metadata();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @AfterEach
    public void after() {
        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }
        forecastSectionRepository.deleteAllInBatch();
        TestTransaction.flagForCommit();
    }

    @Test
    public void testSectionsMetadataRestApi() throws Exception {
        final String response =
            mockMvc.perform(get(API_V1_BASE_PATH + API_METADATA_PART_PATH + FORECAST_SECTIONS_PATH)).andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(API_V1_BASE_PATH + API_METADATA_PART_PATH + FORECAST_SECTIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(DT_JSON_CONTENT_TYPE))
                .andExpect(jsonPath("$.forecastSections.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.forecastSections.features", hasSize(10)))
                .andExpect(jsonPath("$.forecastSections.features[0].geometry.type", is("LineString")))
                .andExpect(jsonPath("$.forecastSections.features[0].geometry.coordinates", hasSize(greaterThan(1))))
                .andExpect(jsonPath("$.forecastSections.features[0].type", is("Feature")))
                .andExpect(jsonPath("$.forecastSections.features[0].id", isA(Integer.class)))
                ;
    }
}
