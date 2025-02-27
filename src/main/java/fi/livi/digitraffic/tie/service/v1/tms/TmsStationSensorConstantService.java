package fi.livi.digitraffic.tie.service.v1.tms;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.tie.dao.v1.TmsSensorConstantDao;
import fi.livi.digitraffic.tie.external.lotju.metadata.lam.LamAnturiVakioArvoVO;
import fi.livi.digitraffic.tie.external.lotju.metadata.lam.LamAnturiVakioVO;
import fi.livi.digitraffic.tie.helper.DateHelper;
import fi.livi.digitraffic.tie.model.DataType;
import fi.livi.digitraffic.tie.service.DataStatusService;

@Service
public class TmsStationSensorConstantService {
    private static final Logger log = LoggerFactory.getLogger(TmsStationSensorConstantService.class);
    private final TmsSensorConstantDao tmsSensorConstantDao;
    private final DataStatusService dataStatusService;

    @Autowired
    public TmsStationSensorConstantService(final TmsSensorConstantDao tmsSensorConstantDao,
                                           final DataStatusService dataStatusService) {

        this.tmsSensorConstantDao = tmsSensorConstantDao;
        this.dataStatusService = dataStatusService;
    }

    @Transactional
    public boolean updateSensorConstant(final LamAnturiVakioVO anturiVakio) {
        return tmsSensorConstantDao.updateSensorConstants(Collections.singletonList(anturiVakio)) > 0;
    }

    @Transactional
    public boolean updateSensorConstants(final List<LamAnturiVakioVO> allLamAnturiVakios) {
        final List<Long> ids = allLamAnturiVakios.stream().map(LamAnturiVakioVO::getId).collect(Collectors.toList());
        final int obsoleted = tmsSensorConstantDao.obsoleteSensorConstantsExcludingIds(ids);
        final int upsert = tmsSensorConstantDao.updateSensorConstants(allLamAnturiVakios);
        log.info("updateSensorConstants upsertCount={}, obsoleteCount={}", upsert, obsoleted);
        return obsoleted > 0 || upsert > 0;
    }

    @Transactional(readOnly = true)
    public boolean obsoleteSensorConstantWithLotjuId(final long lotjuId) {
        return tmsSensorConstantDao.obsoleteSensorConstantWithLotjuId(lotjuId) > 0;
    }

    @Transactional
    public boolean updateSingleSensorConstantValues(final List<LamAnturiVakioArvoVO> lamAnturiVakioArvos) {
        lamAnturiVakioArvos.forEach(v -> tmsSensorConstantDao.updateSensorConstantValueToObsoleteWithSensorConstantValueLotjuId(v.getAnturiVakioId()));
        final int upsert = tmsSensorConstantDao.updateSensorConstantValues(lamAnturiVakioArvos);
        log.info("method=updateSingleSensorConstantValues upsert={}", upsert);
        return upsert > 0;
    }

    @Transactional
    public boolean updateSensorConstantValues(final List<LamAnturiVakioArvoVO> allLamAnturiVakioArvos) {
        final List<Long> ids = allLamAnturiVakioArvos.stream().map(LamAnturiVakioArvoVO::getId).collect(Collectors.toList());
        final int obsoleted = tmsSensorConstantDao.updateSensorSensorConstantValuesToObsoleteExcludingIds(ids);
        final int upsert = tmsSensorConstantDao.updateSensorConstantValues(allLamAnturiVakioArvos);
        log.info("method=updateSensorConstantValues upsert={}, obsoleteCount={}", upsert, obsoleted);
        return obsoleted > 0 || upsert > 0;
    }

    @Transactional(readOnly = true)
    public boolean updateSensorConstantValueToObsoleteWithSensorConstantValueLotjuId(final long sensorConstantValueLotjuId) {
        return tmsSensorConstantDao.updateSensorConstantValueToObsoleteWithSensorConstantValueLotjuId(sensorConstantValueLotjuId) > 0;
    }

    @Transactional(readOnly = true)
    public Instant getLatestMeasurementTime() {
        final Instant dataUpdated = dataStatusService.findDataUpdatedInstant(DataType.TMS_SENSOR_CONSTANT_VALUE_DATA);
        final Instant metadataUpdated = dataStatusService.findDataUpdatedInstant(DataType.TMS_SENSOR_CONSTANT_METADATA);
        return DateHelper.getGreatest(dataUpdated, metadataUpdated);
    }
}
