package fi.livi.digitraffic.tie.dto.maintenance.old;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import fi.livi.digitraffic.tie.metadata.geojson.Feature;
import fi.livi.digitraffic.tie.metadata.geojson.Geometry;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON Feature Object.", name = "MaintenanceTrackingFeatureOld")
@JsonPropertyOrder({ "type", "properties", "geometry" })
public class MaintenanceTrackingFeature extends Feature<Geometry<?>, MaintenanceTrackingProperties> {

    public MaintenanceTrackingFeature(final Geometry<?> geometry, final MaintenanceTrackingProperties properties) {
        super(geometry, properties);
    }

    @Schema(description = "GeoJSON Feature Object", required = true, allowableValues = "Feature")
    @Override
    public String getType() {
        return "Feature";
    }


    @Schema(description = "GeoJSON Point or LineString Geometry Object containing route point(s)", required = true)
    @Override
    public Geometry<?> getGeometry() {
        return super.getGeometry();
    }
}
