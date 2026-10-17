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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantRegistry;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.extensions.multitenancy.components.eventstore.MultiTenantEventStore;
import org.axonframework.extensions.multitenancy.autoconfig.MultiTenantDataSourceManager;
import org.axonframework.extensions.multitenancy.autoconfig.TenantCapabilityDiscoveryModeRegistry;
import org.axonframework.extensions.multitenancy.autoconfig.TenantMemberCapabilitiesController;
import org.axonframework.extensions.multitenancy.autoconfig.MultiTenancySpringCloudAutoConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.sql.DataSource;

import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class MultiTenancyJpaSpringCloudIntegrationTest {

    private static final String TENANT_ID = "tenant-1";
    private static final TenantDescriptor TENANT = TenantDescriptor.tenantWithId(TENANT_ID);
    private static final TenantDescriptor SECOND_TENANT = TenantDescriptor.tenantWithId("tenant-2");

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner()
                .withSystemProperties("disable-axoniq-console-message=true")
                .withPropertyValues(
                        "axon.axonserver.enabled=false",
                        "axon.distributed.enabled=true",
                        "axon.multi-tenancy.enabled=true",
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
    void runsWithoutAxonServerUsingJpaEventStoreAndSpringCloudCommandSegments() throws Exception {
        testApplicationContext.run(context -> {
            MultiTenantCommandBus commandBus = assertInstanceOf(
                    MultiTenantCommandBus.class, context.getBean(CommandBus.class)
            );
            TenantRegistry tenantRegistry = context.getBean(TenantRegistry.class);
            assertTrue(tenantRegistry.registerTenant(SECOND_TENANT));
            assertEquals(2, commandBus.tenantSegments().size());
            assertInstanceOf(DistributedCommandBus.class, commandBus.tenantSegments().get(TENANT));
            assertInstanceOf(DistributedCommandBus.class, commandBus.tenantSegments().get(SECOND_TENANT));
            assertInstanceOf(
                    org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode.class,
                    context.getBean(TenantCapabilityDiscoveryModeRegistry.class).discoveryMode(SECOND_TENANT)
            );
            assertEquals(1, context.getBeansOfType(MultiTenantDataSourceManager.class).size());

            commandBus.subscribe(String.class.getName(), command -> "handled-" + command.getPayload());
            CompletableFuture<Object> commandResult = new CompletableFuture<>();
            commandBus.dispatch(
                    GenericCommandMessage.asCommandMessage("runtime")
                                         .andMetaData(Map.of(TENANT_CORRELATION_KEY,
                                                             SECOND_TENANT.tenantId())),
                    (command, result) -> {
                        if (result.isExceptional()) {
                            commandResult.completeExceptionally(result.exceptionResult());
                        } else {
                            commandResult.complete(result.getPayload());
                        }
                    }
            );
            assertEquals("handled-runtime", commandResult.get(5, TimeUnit.SECONDS));

            @SuppressWarnings("unchecked")
            Function<TenantDescriptor, DataSource> resolver = (Function<TenantDescriptor, DataSource>) context
                    .getBean("tenantDataSourceResolver");
            JdbcDataSource firstDataSource = (JdbcDataSource) resolver.apply(TENANT);
            JdbcDataSource secondDataSource = (JdbcDataSource) resolver.apply(SECOND_TENANT);
            assertTrue(!firstDataSource.getURL().equals(secondDataSource.getURL()));

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

    @Test
    void exposesCapabilitiesForConfiguredAndNewlyRegisteredTenantCommandSegments() {
        testApplicationContext.run(context -> {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(TenantMemberCapabilitiesController.class))
                                         .addPlaceholderValue("axon.distributed.spring-cloud.rest-mode-url",
                                                              "/member-capabilities")
                                         .build();
            mvc.perform(get("/member-capabilities/" + TENANT_ID))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.loadFactor").value(100));

            assertTrue(context.getBean(TenantRegistry.class).registerTenant(SECOND_TENANT));
            mvc.perform(get("/member-capabilities/" + SECOND_TENANT.tenantId()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.loadFactor").value(100));
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @ImportAutoConfiguration(DiscoveryAutoConfiguration.class)
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

        @Bean(name = "tenantDataSourceResolver")
        public Function<TenantDescriptor, DataSource> tenantDataSourceResolver() {
            return tenant -> {
                JdbcDataSource dataSource = new JdbcDataSource();
                dataSource.setURL(
                        "jdbc:h2:mem:multi-tenancy-jpa-spring-cloud-" + tenant.tenantId() + ";DB_CLOSE_DELAY=-1"
                );
                return dataSource;
            };
        }
    }

    @AutoConfiguration(after = MultiTenancySpringCloudAutoConfiguration.class)
    static class DiscoveryAutoConfiguration {

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
