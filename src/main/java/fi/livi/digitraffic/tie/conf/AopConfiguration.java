package fi.livi.digitraffic.tie.conf;

import fi.livi.digitraffic.tie.aop.TransactionLoggerAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import fi.livi.digitraffic.tie.aop.PerformanceMonitorAspect;
import fi.livi.digitraffic.tie.aop.ScheduledJobLogger;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Aspect
public class AopConfiguration {

    @Bean
    public PerformanceMonitorAspect performanceMonitorAspect() {
        return new PerformanceMonitorAspect();
    }

    @Bean
    public ScheduledJobLogger scheduleJobLogger() {
        return new ScheduledJobLogger();
    }

    @Bean
    public TransactionLoggerAspect transactionLoggerAspect(@Value("${dt.logging.transaction.limit}") final int limit) { return new TransactionLoggerAspect(limit); }
}
