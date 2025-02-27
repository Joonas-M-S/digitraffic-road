package fi.livi.digitraffic.tie.data.dao;

import static fi.livi.digitraffic.tie.TestUtils.commitAndEndTransactionAndStartNew;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.digitraffic.tie.AbstractServiceTest;
import fi.livi.digitraffic.tie.dao.v2.V2MaintenanceTrackingRepository;
import fi.livi.digitraffic.tie.dto.maintenance.v1.MaintenanceTrackingDomainDtoV1;
import fi.livi.digitraffic.tie.service.v3.maintenance.V3MaintenanceTrackingServiceTestHelper;

public class MaintenanceTrackingRepositoryTest extends AbstractServiceTest {

    @Autowired
    private V2MaintenanceTrackingRepository maintenanceTrackingRepository;

    @Autowired
    private V3MaintenanceTrackingServiceTestHelper testHelper;

    private final static String DOMAIN_FOUND = "domain-1";
    private final static String DOMAIN_NOT_FOUND = "domain-2";

    @BeforeEach
    public void initDb() {
        testHelper.insertDomain(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN, V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN);
        commitAndEndTransactionAndStartNew();
    }

    @AfterEach
    public void cleanDb() {
        testHelper.deleteDomains(DOMAIN_FOUND, DOMAIN_NOT_FOUND);
        commitAndEndTransactionAndStartNew();
    }

    @Test
    public void getDomainsWithGenerics() {
        final String DOMAIN_FOUND = "domain-1";
        final String DOMAIN_NOT_FOUND = "domain-2";
        testHelper.insertDomain(DOMAIN_FOUND, "Foo/Bar");
        testHelper.insertDomain(DOMAIN_NOT_FOUND, null);
        commitAndEndTransactionAndStartNew();
        final List<MaintenanceTrackingDomainDtoV1> domains = maintenanceTrackingRepository.getDomainsWithGenerics();
        final List<String> names = domains.stream().map(MaintenanceTrackingDomainDtoV1::getName).collect(Collectors.toList());

        Assertions.assertTrue(names.contains(DOMAIN_FOUND));
        Assertions.assertFalse(names.contains(DOMAIN_NOT_FOUND));
        Assertions.assertTrue(names.contains(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN));
        Assertions.assertTrue(names.contains(V2MaintenanceTrackingRepository.GENERIC_ALL_DOMAINS));
        Assertions.assertTrue(names.contains(V2MaintenanceTrackingRepository.GENERIC_MUNICIPALITY_DOMAINS));
    }

    @Test
    public void getRealDomainNames() {
        final String DOMAIN_FOUND = "domain-1";
        final String DOMAIN_NOT_FOUND = "domain-2";
        testHelper.insertDomain(DOMAIN_FOUND, "Foo/Bar");
        testHelper.insertDomain(DOMAIN_NOT_FOUND, null);
        commitAndEndTransactionAndStartNew();
        final List<String> domainNames = maintenanceTrackingRepository.getRealDomainNames();

        Assertions.assertTrue(domainNames.contains(DOMAIN_FOUND));
        Assertions.assertFalse(domainNames.contains(DOMAIN_NOT_FOUND));
        Assertions.assertTrue(domainNames.contains(V2MaintenanceTrackingRepository.STATE_ROADS_DOMAIN));
        Assertions.assertFalse(domainNames.contains(V2MaintenanceTrackingRepository.GENERIC_ALL_DOMAINS));
        Assertions.assertFalse(domainNames.contains(V2MaintenanceTrackingRepository.GENERIC_MUNICIPALITY_DOMAINS));
    }

}
