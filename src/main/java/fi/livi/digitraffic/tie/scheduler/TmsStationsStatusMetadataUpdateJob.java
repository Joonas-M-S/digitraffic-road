package fi.livi.digitraffic.tie.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.digitraffic.tie.model.DataType;
import fi.livi.digitraffic.tie.service.v1.tms.TmsStationUpdater;

@DisallowConcurrentExecution
public class TmsStationsStatusMetadataUpdateJob extends SimpleUpdateJob {

    @Autowired
    private TmsStationUpdater tmsStationUpdater;

    @Override
    protected void doExecute(JobExecutionContext context) {
        final int tmsCount = tmsStationUpdater.updateTmsStationsStatuses();
        dataStatusService.updateDataUpdated(DataType.TMS_STATION_METADATA_CHECK);
        log.info("method=doExecute updateCount={} TMS stations statuses", tmsCount);
    }
}
