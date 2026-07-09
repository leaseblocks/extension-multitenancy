/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.multitenancy.integration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTenancyJpaSpringCloudIntegrationTest {

    private static final String TENANT_ID = "tenant-1";
    private static final TenantDescriptor TENANT = TenantDescriptor.tenantWithId(TENANT_ID);

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner()
                .withSystemProperties("disable-axoniq-console-message=true")
                .withPropertyValues(
                        "axon.axonserver.enabled=false",
                        "axon.distributed.enabled=true",
                        "axon.distributed.spring-cloud.enable-accept-all-commands=true",
                        "axon.multi-tenancy.tenants=" + TENANT_ID,
                        "spring.application.name=multi-tenancy-jpa-spring-cloud-test",
                        "spring.autoconfigure.exclude=" +
                                "org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration",
                        "spring.datasource.url=jdbc:h2:mem:multi-tenancy-jpa-spring-cloud;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.jpa.hibernate.ddl-auto=create-drop"
                )
                .withUserConfiguration(JpaSpringCloudContext.class);
    }

    @Test
    void runsWithoutAxonServerUsingJpaEventStoreAndSpringCloudCommandSegments() {
        testApplicationContext.run(context -> {
            MultiTenantCommandBus commandBus = assertInstanceOf(
                    MultiTenantCommandBus.class, context.getBean(CommandBus.class)
            );
            assertEquals(1, commandBus.tenantSegments().size());
            assertInstanceOf(DistributedCommandBus.class, commandBus.tenantSegments().get(TENANT));

            MultiTenantEventStore eventStore = assertInstanceOf(
                    MultiTenantEventStore.class, context.getBean(EventStore.class)
            );
            EventStore tenantEventStore = eventStore.tenantSegments().get(TENANT);
            assertInstanceOf(EmbeddedEventStore.class, tenantEventStore);
            assertInstanceOf(
                    JpaEventStorageEngine.class,
                    ReflectionTestUtils.getField(tenantEventStore, "storageEngine")
            );

            String aggregateIdentifier = "aggregate-" + TENANT_ID;
            eventStore.publish(
                    new GenericDomainEventMessage<>("test", aggregateIdentifier, 0, "payload")
                            .andMetaData(Map.of(TENANT_CORRELATION_KEY, TENANT_ID))
            );

            assertTrue(eventStore.readEvents(aggregateIdentifier, TENANT).hasNext());
            assertEquals("payload", eventStore.readEvents(aggregateIdentifier, TENANT).next().getPayload());
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class JpaSpringCloudContext {

        @Bean
        public Registration localServiceInstance() {
            return new TestRegistration(
                    "multi-tenancy-jpa-spring-cloud-test",
                    "localhost",
                    8080
            );
        }

        @Bean
        public DiscoveryClient discoveryClient(Registration localServiceInstance) {
            return new DiscoveryClient() {
                @Override
                public String description() {
                    return "Static test discovery client";
                }

                @Override
                public List<ServiceInstance> getInstances(String serviceId) {
                    if (localServiceInstance.getServiceId().equals(serviceId)) {
                        return Collections.singletonList(localServiceInstance);
                    }
                    return Collections.emptyList();
                }

                @Override
                public List<String> getServices() {
                    return Collections.singletonList(localServiceInstance.getServiceId());
                }
            };
        }
    }

    private static class TestRegistration extends DefaultServiceInstance implements Registration {

        private TestRegistration(String serviceId, String host, int port) {
            super(serviceId + "-1", serviceId, host, port, false);
        }
    }
}
