package fi.livi.digitraffic.tie.service.v1.location;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.tie.dao.v1.location.LocationTypeRepository;
import fi.livi.digitraffic.tie.model.v1.location.LocationType;

@ConditionalOnNotWebApplication
@Service
public class LocationTypeUpdater {
    private final LocationTypeRepository locationTypeRepository;

    public LocationTypeUpdater(final LocationTypeRepository locationTypeRepository) {
        this.locationTypeRepository = locationTypeRepository;
    }

    @Transactional
    public List<LocationType> updateLocationTypes(final Path path, final String version) {
        final LocationTypeReader locationTypeReader = new LocationTypeReader(version);
        final List<LocationType> newTypes = locationTypeReader.read(path);

        locationTypeRepository.saveAll(newTypes);

        return newTypes;
    }
}
