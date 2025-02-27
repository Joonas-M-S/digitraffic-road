package fi.livi.digitraffic.tie.conf.metrics;

import static fi.livi.digitraffic.tie.conf.metrics.HikariCPMetrics.CONNECTIONS_ACTIVE;
import static fi.livi.digitraffic.tie.conf.metrics.HikariCPMetrics.CONNECTIONS_MAX;
import static fi.livi.digitraffic.tie.conf.metrics.HikariCPMetrics.CONNECTIONS_PENDING;
import static fi.livi.digitraffic.tie.conf.metrics.HikariCPMetrics.TAG_POOL;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import fi.livi.digitraffic.tie.aop.NoJobLogging;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;

/**
 * Measure pool statistics every 100ms and log min and max once a minute.
 */
@Configuration
public class MetricWriterConfiguration {
    private final MeterRegistry meterRegistry;

    private static final Logger LOG = LoggerFactory.getLogger(MetricWriterConfiguration.class);

    private final List<LoggableMetric> metricsToLog = Arrays.asList(
        LoggableMetric.of("process.cpu.usage"),
        LoggableMetric.of("system.cpu.count"),
        LoggableMetric.of("jvm.memory.used").withTag("area"),
        LoggableMetric.of(CONNECTIONS_MAX).withTag(TAG_POOL).noMin(),
        LoggableMetric.of(CONNECTIONS_PENDING).withTag(TAG_POOL),
        LoggableMetric.of(CONNECTIONS_ACTIVE).withTag(TAG_POOL)
    );

    private static final Map<MetricKey, Double> metricMap = new HashMap<>();

    private static class MetricKey {
        public final String metric;
        public final String tag;

        private MetricKey(final String metric, final String tag) {
            this.metric = metric;
            this.tag = tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MetricKey metricKey = (MetricKey) o;
            return Objects.equals(metric, metricKey.metric) &&
                Objects.equals(tag, metricKey.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metric, tag);
        }
    }

    public MetricWriterConfiguration(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedRate = 1000*60)
    @NoJobLogging
    void printMetrics() {
        metricMap.keySet().forEach(this::logMeasurement);

        //logAllAvailableMetrics();

        metricMap.clear();
    }

    @Scheduled(fixedRate = 50)
    @NoJobLogging
    void updateMetrics() {
        metricsToLog.forEach(this::updateMeasurement);
    }

    private void logAllAvailableMetrics() {
        meterRegistry.forEachMeter(m ->
            m.measure().forEach(measure ->
                LOG.info("metric {} measure {}", m.getId(), measure.toString())));
    }

    private Collection<Meter> findMetrics(final LoggableMetric metric) {
        final RequiredSearch requiredSearch = meterRegistry.get(metric.metricKey);

        try {
            return requiredSearch.meters();
        } catch(final Exception e) {
            return null;
        }
    }

    private void updateMeasurement(final LoggableMetric metric) {
        final Collection<Meter> meters = findMetrics(metric);

        if(meters == null) {
            LOG.error("Could not find meter {}", metric.metricKey);
            return;
        }

        meters.forEach(m -> updateMeter(m, metric));
    }

    private void updateMeter(final Meter meter, final LoggableMetric metric) {
        final Measurement measurement = StreamSupport.stream(meter.measure().spliterator(), false)
            .filter(m -> m.getStatistic() == metric.statistic)
            .findFirst().orElse(null);

        if(measurement == null) {
            LOG.error("Could not find statistic {} for {}", metric.statistic, metric.metricKey);
            return;
        }

        final String tagValue = metric.tagName == null ? null : meter.getId().getTag(metric.tagName);

        if(metric.logMin) {
            final MetricKey metricKey = new MetricKey(metric.metricKey + ".min", tagValue);

            final Double oldValue = metricMap.get(metricKey);
            final Double newValue = oldValue == null ? measurement.getValue() : Math.min(oldValue, measurement.getValue());

            metricMap.put(metricKey, newValue);
        }

        if(metric.logMax) {
            final MetricKey metricKey = new MetricKey(metric.metricKey + ".max", tagValue);

            final Double oldValue = metricMap.get(metricKey);
            final Double newValue = oldValue == null ? measurement.getValue() : Math.max(oldValue, measurement.getValue());

            metricMap.put(metricKey, newValue);
        }
    }

    private void logMeasurement(final MetricKey metricKey) {
        final Double value = metricMap.get(metricKey);

        if(value != null) {
            // must set root-locale to use . as decimal separator
            if(metricKey.tag != null) {
                LOG.info(String.format(Locale.ROOT, "meterName=%s statisticValue=%.02f tagName=%s", metricKey.metric, value, metricKey.tag));
            } else {
                LOG.info(String.format(Locale.ROOT, "meterName=%s statisticValue=%.02f", metricKey.metric, value));
            }
        }
    }
}
