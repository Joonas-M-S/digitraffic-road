
package fi.livi.digitraffic.tie.dto.v2.trafficannouncement.geojson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import fi.livi.digitraffic.tie.helper.ToStringHelper;
import fi.livi.digitraffic.tie.model.JsonAdditionalProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Location to display in ETRS-TM35FIN coordinate format.", name = "LocationToDisplay_OldV2")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "e",
    "n"
})
public class LocationToDisplay extends JsonAdditionalProperties {

    @Schema(description = "ETRS-TM35FIN east coordinate", required = true)
    public Double e;

    @Schema(description = "ETRS-TM35FIN north coordinate", required = true)
    public Double n;

    public LocationToDisplay(Double e, Double n) {
        super();
        this.e = e;
        this.n = n;
    }

    @Override
    public String toString() {
        return ToStringHelper.toStringFull(this);
    }
}
