package fi.livi.digitraffic.tie.service.v1.lotju;

import java.net.URI;

import javax.xml.bind.JAXBElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.client.support.destination.DestinationProvider;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import fi.livi.digitraffic.tie.conf.properties.LotjuMetadataProperties;

public abstract class AbstractLotjuMetadataClient extends WebServiceGatewaySupport {


    /**
     *
     * @param marshaller Marshaller for SOAP messages
     * @param lotjuMetadataProperties properties for metadata fetch
     * @param dataPath ie. /data/service
     */
    public AbstractLotjuMetadataClient(final Jaxb2Marshaller marshaller, final LotjuMetadataProperties lotjuMetadataProperties, final String dataPath) {
        setWebServiceTemplate(new WebServiceTemplateWithMultiDestinationProviderSupport());
        setDestinationProvider(new MultiDestinationProvider(
            HostWithHealthCheck.createHostsWithHealthCheck(lotjuMetadataProperties, dataPath)));

        setMarshaller(marshaller);
        setUnmarshaller(marshaller);

        final HttpComponentsMessageSender sender = new HttpComponentsMessageSender();
        sender.setConnectionTimeout(lotjuMetadataProperties.getSender().connectionTimeout);
        sender.setReadTimeout(lotjuMetadataProperties.getSender().readTimeout);
        setMessageSender(sender);

    }

    protected Object marshalSendAndReceive(final JAXBElement<?> requestPayload) {
        return getWebServiceTemplate().marshalSendAndReceive(requestPayload);
    }

    public static class WebServiceTemplateWithMultiDestinationProviderSupport extends WebServiceTemplate {

        private static final Logger log = LoggerFactory.getLogger(WebServiceTemplateWithMultiDestinationProviderSupport.class);

        @Override
        public Object marshalSendAndReceive(final Object requestPayload, final WebServiceMessageCallback requestCallback) {
            final DestinationProvider dp = getDestinationProvider();

            if ( dp instanceof MultiDestinationProvider ) {
                final MultiDestinationProvider mdp = (MultiDestinationProvider) dp;
                int tryCount = 0;

                Exception lastException;
                do {
                    tryCount++;
                    final URI dest = mdp.getDestination();
                    String dataUri = null;
                    try {
                        dataUri = getDefaultUri();
                        final Object value = marshalSendAndReceive(dataUri, requestPayload, requestCallback);
                        // mark host as healthy
                        mdp.setHostHealthy(dest);
                        return value;
                    } catch (Exception e) {
                        // mark host not healthy
                        mdp.setHostNotHealthy(dest);
                        lastException = e;
                        log.warn("method=marshalSendAndReceive returned error for dataUrl={} reason: {}", dataUri, lastException.getMessage());
                    }
                } while (tryCount < mdp.getDestinationsCount());
                throw new IllegalStateException(String.format("No host found to return data without error dataUrls=%s", mdp.getDestinationsAsString()),
                                                lastException);
            }

            return marshalSendAndReceive(getDefaultUri(), requestPayload, requestCallback);
        }
    }
}
