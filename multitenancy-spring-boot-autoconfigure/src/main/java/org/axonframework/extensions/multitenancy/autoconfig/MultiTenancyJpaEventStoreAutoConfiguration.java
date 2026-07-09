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
package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration for tenant-specific JPA event stores without Axon Server.
 *
 * @author Axon Framework
 * @since 4.12.1
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${axon.axonserver.enabled:true}' == 'false' and '${axon.multi-tenancy.enabled:true}' != 'false'"
)
@ConditionalOnClass(name = {
        "jakarta.persistence.EntityManager",
        "org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine"
})
@ConditionalOnBean(type = "org.axonframework.common.jpa.EntityManagerProvider")
@AutoConfigureAfter(name = {
        "org.axonframework.springboot.autoconfig.JpaAutoConfiguration",
        "org.axonframework.springboot.autoconfig.JpaEventStoreAutoConfiguration"
})
@AutoConfigureBefore(MultiTenancyLocalSegmentAutoConfiguration.class)
public class MultiTenancyJpaEventStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(PersistenceExceptionResolver.class)
    @ConditionalOnMissingBean
    public TenantEventSegmentFactory tenantJpaEventSegmentFactory(
            Serializer defaultSerializer,
            PersistenceExceptionResolver persistenceExceptionResolver,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            Configuration axonConfiguration,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager
    ) {
        return tenant -> {
            JpaEventStorageEngine storageEngine =
                    JpaEventStorageEngine.builder()
                                         .snapshotSerializer(defaultSerializer)
                                         .upcasterChain(axonConfiguration.upcasterChain())
                                         .persistenceExceptionResolver(persistenceExceptionResolver)
                                         .eventSerializer(eventSerializer)
                                         .snapshotFilter(axonConfiguration.snapshotFilter())
                                         .entityManagerProvider(entityManagerProvider)
                                         .transactionManager(new TenantWrappedTransactionManager(
                                                 transactionManager, tenant
                                         ))
                                         .build();

            return EmbeddedEventStore.builder()
                                     .storageEngine(storageEngine)
                                     .messageMonitor(axonConfiguration.messageMonitor(
                                             EventStore.class, "eventStore@" + tenant.tenantId()
                                     ))
                                     .spanFactory(axonConfiguration.getComponent(EventBusSpanFactory.class))
                                     .build();
        };
    }
}
