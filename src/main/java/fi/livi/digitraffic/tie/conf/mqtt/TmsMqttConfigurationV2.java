package fi.livi.digitraffic.tie.conf.mqtt;

import static fi.livi.digitraffic.tie.service.v1.MqttRelayQueue.StatisticsType.TMS;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.livi.digitraffic.tie.aop.NoJobLogging;
import fi.livi.digitraffic.tie.dto.v1.SensorValueDtoV1;
import fi.livi.digitraffic.tie.helper.MqttUtil;
import fi.livi.digitraffic.tie.model.RoadStationType;
import fi.livi.digitraffic.tie.mqtt.MqttDataMessageV2;
import fi.livi.digitraffic.tie.mqtt.MqttMessageSenderV2;
import fi.livi.digitraffic.tie.service.ClusteredLocker;
import fi.livi.digitraffic.tie.service.roadstation.v1.RoadStationSensorServiceV1;
import fi.livi.digitraffic.tie.service.v1.MqttRelayQueue;

@ConditionalOnProperty("mqtt.tms.v2.enabled")
@ConditionalOnNotWebApplication
@Component
public class TmsMqttConfigurationV2 {
    // tms-v2/{roadStationId}/{sensorId}
    private static final String TMS_TOPIC = "tms-v2/%d/%d";
    private static final String TMS_STATUS_TOPIC = "tms-v2/status";

    private final RoadStationSensorServiceV1 roadStationSensorService;
    private final MqttMessageSenderV2 mqttMessageSender;

    private static final Logger LOGGER = LoggerFactory.getLogger(TmsMqttConfigurationV2.class);

    public TmsMqttConfigurationV2(final MqttRelayQueue mqttRelay,
                                final RoadStationSensorServiceV1 roadStationSensorService,
                                final ObjectMapper objectMapper,
                                final ClusteredLocker clusteredLocker) {

        this.mqttMessageSender = new MqttMessageSenderV2(LOGGER, mqttRelay, objectMapper, TMS, clusteredLocker);
        this.roadStationSensorService = roadStationSensorService;

        mqttMessageSender.setLastUpdated(roadStationSensorService.getLatestSensorValueUpdatedTime(RoadStationType.TMS_STATION));
    }

    @NoJobLogging
    @Scheduled(fixedDelayString = "${mqtt.tms.v2.pollingIntervalMs}")
    public void pollAndSendMessages() {
        if (mqttMessageSender.acquireLock()) {
            try {
                final List<SensorValueDtoV1> sensorValues =
                    roadStationSensorService.findAllPublicNonObsoleteRoadStationSensorValuesUpdatedAfter(mqttMessageSender.getLastUpdated(), RoadStationType.TMS_STATION);

                if(!sensorValues.isEmpty()) {
                    final Instant lastUpdated = sensorValues.stream()
                        .max(Comparator.comparing(SensorValueDtoV1::getUpdatedTime))
                        .map(SensorValueDtoV1::getUpdatedTime)
                        .orElseThrow();
                    final List<MqttDataMessageV2> dataMessages = sensorValues.stream().map(this::createMqttDataMessage).collect(Collectors.toList());

                    mqttMessageSender.sendMqttMessages(lastUpdated, dataMessages);
                }
            } catch (final Exception e) {
                LOGGER.error("Polling failed", e);
            }
        } else {
            mqttMessageSender.setLastUpdated(roadStationSensorService.getLatestSensorValueUpdatedTime(RoadStationType.TMS_STATION));
        }
    }

    @Scheduled(fixedDelayString = "${mqtt.status.intervalMs}")
    public void sendStatusMessage() {
        if (mqttMessageSender.acquireLock()) {
            mqttMessageSender.sendStatusMessage(TMS_STATUS_TOPIC);
        }
    }

    private MqttDataMessageV2 createMqttDataMessage(final SensorValueDtoV1 sv) {
        return MqttDataMessageV2.createV2(MqttUtil.getTopicForMessage(TMS_TOPIC, sv.getRoadStationNaturalId(), sv.getSensorNaturalId()), sv);
    }
}
