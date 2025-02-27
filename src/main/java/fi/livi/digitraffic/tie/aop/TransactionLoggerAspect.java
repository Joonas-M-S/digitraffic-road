package fi.livi.digitraffic.tie.aop;

import org.apache.commons.lang3.time.StopWatch;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

@Aspect
@Order
public class TransactionLoggerAspect {
    private static final Logger log = LoggerFactory.getLogger("TransactionLogger");

    private final int limit;

    public TransactionLoggerAspect(final int limit) {
        this.limit = limit;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitor(final ProceedingJoinPoint pjp) throws Throwable {
        final StopWatch stopWatch = StopWatch.createStarted();
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        final String className = methodSignature.getDeclaringType().getSimpleName();
        final String methodName = methodSignature.getName();
        final Object[] args = pjp.getArgs();

        try {
            return pjp.proceed();
        } finally {
            final long tookMs = stopWatch.getTime();

            if(tookMs > limit) {
                final StringBuilder arguments = new StringBuilder(100);
                PerformanceMonitorAspect.buildValueToString(arguments, args);
                log.info("Transaction method={}.{} arguments={} tookMs={}", className, methodName, arguments, tookMs);
            }
        }
    }
}
