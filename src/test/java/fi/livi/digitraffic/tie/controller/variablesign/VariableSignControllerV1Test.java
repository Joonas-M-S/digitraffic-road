package fi.livi.digitraffic.tie.controller.variablesign;

import static fi.livi.digitraffic.tie.controller.ApiConstants.API_SIGNS;
import static fi.livi.digitraffic.tie.controller.ApiConstants.API_SIGNS_CODE_DESCRIPTIONS;
import static fi.livi.digitraffic.tie.controller.ApiConstants.API_SIGNS_HISTORY;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import fi.livi.digitraffic.tie.AbstractRestWebTest;
import fi.livi.digitraffic.tie.controller.ApiConstants;

public class VariableSignControllerV1Test extends AbstractRestWebTest {
    private ResultActions getJson(final String url) throws Exception {
        final ResultActions response = executeGet(ApiConstants.API_VS_V1 + url);
        logInfoResponse(response);
        return response;
    }

    private void insertTestData() {
        entityManager.createNativeQuery(
            "insert into device(id,updated_date,type,road_address,etrs_tm35fin_x,etrs_tm35fin_y,direction,carriageway) " +
            "values('ID1',current_timestamp, 'NOPEUSRAJOITUS', '1 2 3',10, 20,'KASVAVA', 'NORMAALI');").executeUpdate();

        entityManager.createNativeQuery(
            "insert into device_data(created_date,device_id,display_value,additional_information,effect_date,cause,reliability) " +
                "values (current_timestamp,'ID1','80',null,current_date,null,'NORMAALI');").executeUpdate();

        entityManager.createNativeQuery(
            "insert into device_data_row(device_data_id, screen, row_number, text) " +
                "values ((select id from device_data), 1, 1, 'TEST ROW');").executeUpdate();
    }

    @Test
    public void noData() throws Exception {
        getJson(API_SIGNS)
            .andExpect(status().isOk())
            .andExpect(jsonPath("type", Matchers.equalTo("FeatureCollection")))
            .andExpect(jsonPath("features", Matchers.empty()));
    }

    @Test
    public void notExists() throws Exception {
        getJson(API_SIGNS + "/unknown")
            .andExpect(status().isNotFound());
    }

    @Test
    public void deviceWithData() throws Exception {
        insertTestData();

        getJson(API_SIGNS + "/ID1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("features", Matchers.hasSize(1)))
            .andExpect(jsonPath("features[0].properties.displayValue", Matchers.equalTo("80")));
    }

    @Test
    public void historyNotExists() throws Exception {
        getJson(API_SIGNS_HISTORY + "?deviceId=unknown")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.empty()));
    }

    @Test
    public void historyExists() throws Exception {
        insertTestData();

        getJson(API_SIGNS_HISTORY + "?deviceId=ID1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(1)));
    }

    @Test
    public void codeDescriptionsV3() throws Exception {
        getJson(API_SIGNS_CODE_DESCRIPTIONS)
            .andExpect(status().isOk())
            .andExpect(jsonPath("signTypes", Matchers.hasSize(12)))
            .andExpect(jsonPath("signTypes[0].description", Matchers.notNullValue()))
            .andExpect(jsonPath("signTypes[0].descriptionEn", Matchers.notNullValue()))
        ;
    }
}
