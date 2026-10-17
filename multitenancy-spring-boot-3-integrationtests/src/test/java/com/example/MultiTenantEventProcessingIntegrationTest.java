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

package com.example;

import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.extensions.multitenancy.TenantContext;
import org.axonframework.extensions.multitenancy.autoconfig.MultiTenancyAutoConfiguration;
import org.axonframework.extensions.multitenancy.autoconfig.MultiTenancyLocalSegmentAutoConfiguration;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.configuration.MultiTenantEventProcessingModule;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonTracingAutoConfiguration;
import org.axonframework.springboot.autoconfig.EventProcessingAutoConfiguration;
import org.axonframework.springboot.autoconfig.InfraConfiguration;
import org.axonframework.springboot.autoconfig.NoOpTransactionAutoConfiguration;
import org.axonframework.springboot.autoconfig.ObjectMapperAutoConfiguration;
import org.axonframework.springboot.autoconfig.TransactionAutoConfiguration;
import org.axonframework.springboot.autoconfig.XStreamAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;

class MultiTenantEventProcessingIntegrationTest {

    @Test
    void downstreamEventProcessingConfigurationUsesOnlyTheTenantModule() {
        RecordingEventHandler eventHandler = new RecordingEventHandler();
        new ApplicationContextRunner()
                .withSystemProperties("disable-axoniq-console-message=true")
                .withPropertyValues(
                        "axon.axonserver.enabled=false",
                        "axon.multi-tenancy.enabled=true",
                        "axon.multi-tenancy.tenants=tenant-1,tenant-2"
                )
                .withConfiguration(AutoConfigurations.of(
                        AxonAutoConfiguration.class,
                        AxonTracingAutoConfiguration.class,
                        EventProcessingAutoConfiguration.class,
                        InfraConfiguration.class,
                        MultiTenancyAutoConfiguration.class,
                        MultiTenancyLocalSegmentAutoConfiguration.class,
                        NoOpTransactionAutoConfiguration.class,
                        ObjectMapperAutoConfiguration.class,
                        TransactionAutoConfiguration.class,
                        XStreamAutoConfiguration.class,
                        ApplicationEventProcessingAutoConfiguration.class
                ))
                .withBean(RecordingEventHandler.class, () -> eventHandler)
                .withBean(TenantEventSegmentFactory.class, () -> tenant -> EmbeddedEventStore.builder()
                        .storageEngine(new InMemoryEventStorageEngine())
                        .build())
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EventProcessingConfigurer.class);
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    MultiTenantEventProcessingModule module = context.getBean(MultiTenantEventProcessingModule.class);
                    assertThat(context.getBean(EventProcessingConfigurer.class)).isSameAs(module);
                    assertThat(module.eventProcessor("events", TenantDescriptor.tenantWithId("tenant-1")))
                            .isPresent();
                    assertThat(module.eventProcessor("events", TenantDescriptor.tenantWithId("tenant-2")))
                            .isPresent();

                    EventStore eventStore = context.getBean(EventStore.class);
                    eventStore.publish(new GenericEventMessage<>("first", Map.of(TENANT_CORRELATION_KEY, "tenant-1")));
                    eventStore.publish(new GenericEventMessage<>("second", Map.of(TENANT_CORRELATION_KEY, "tenant-2")));

                    assertThat(eventHandler.handled).containsExactly("tenant-1:first", "tenant-2:second");
                    assertThat(TenantContext.currentTenant()).isNull();
                });
    }

    @AutoConfiguration(after = EventProcessingAutoConfiguration.class)
    static class ApplicationEventProcessingAutoConfiguration {

        @Bean
        ConfigurerModule eventProcessors(EventProcessingModule eventProcessingModule) {
            return configurer -> eventProcessingModule.registerSubscribingEventProcessor("events");
        }
    }

    @ProcessingGroup("events")
    static class RecordingEventHandler {

        private final List<String> handled = new ArrayList<>();

        @EventHandler
        public void on(String event) {
            handled.add(TenantContext.currentTenant().tenantId() + ":" + event);
        }
    }
}
