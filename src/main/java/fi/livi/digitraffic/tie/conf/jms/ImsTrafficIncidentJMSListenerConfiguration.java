package fi.livi.digitraffic.tie.conf.jms;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import fi.livi.digitraffic.tie.service.ClusteredLocker;
import fi.livi.digitraffic.tie.service.jms.JMSMessageListener;
import fi.livi.digitraffic.tie.service.jms.marshaller.ImsMessageMarshaller;
import fi.livi.digitraffic.tie.service.v2.datex2.V2Datex2UpdateService;
import progress.message.jclient.QueueConnectionFactory;

@ConditionalOnProperty(name = "jms.datex2.inQueue")
@Configuration
public class ImsTrafficIncidentJMSListenerConfiguration extends AbstractJMSListenerConfiguration<ExternalIMSMessage> {
    private static final Logger log = LoggerFactory.getLogger(ImsTrafficIncidentJMSListenerConfiguration.class);

    private final Jaxb2Marshaller imsJaxb2Marshaller;
    private final V2Datex2UpdateService v2Datex2UpdateService;

    @Autowired
    public ImsTrafficIncidentJMSListenerConfiguration(@Qualifier("sonjaJMSConnectionFactory") QueueConnectionFactory connectionFactory,
                                                      @Value("${jms.userId}") final String jmsUserId,
                                                      @Value("${jms.password}") final String jmsPassword,
                                                      @Value("#{'${jms.datex2.inQueue}'.split(',')}")
                                                      final List<String> jmsQueueKeys,
                                                      final ClusteredLocker clusteredLocker,
                                                      @Qualifier("imsJaxb2Marshaller") final Jaxb2Marshaller imsJaxb2Marshaller,
                                                      final V2Datex2UpdateService v2Datex2UpdateService) {

        super(connectionFactory,
                clusteredLocker,
              log);
        this.imsJaxb2Marshaller = imsJaxb2Marshaller;
        this.v2Datex2UpdateService = v2Datex2UpdateService;

        setJmsParameters(new JMSParameters(jmsQueueKeys, jmsUserId, jmsPassword,
                                           ImsTrafficIncidentJMSListenerConfiguration.class.getSimpleName(),
                                           ClusteredLocker.generateInstanceId()));
    }

    @Override
    public JMSMessageListener<ExternalIMSMessage> createJMSMessageListener() {
        final JMSMessageListener.JMSDataUpdater<ExternalIMSMessage> handleData = v2Datex2UpdateService::updateTrafficDatex2ImsMessages;
        final ImsMessageMarshaller messageMarshaller = new ImsMessageMarshaller(imsJaxb2Marshaller);

        return new JMSMessageListener<>(messageMarshaller, handleData,
                                        isQueueTopic(getJmsParameters().getJmsQueueKeys()),
                                        log);
    }

}
