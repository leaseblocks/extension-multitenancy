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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantConnectPredicate;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.multitenancy.components.eventstore.TenantEventSegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.MultiTenantQueryUpdateEmitter;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQuerySegmentFactory;
import org.axonframework.extensions.multitenancy.components.queryhandeling.TenantQueryUpdateEmitterSegmentFactory;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryBusSpanFactory;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.QueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Autoconfiguration for tenant segments that do not require Axon Server.
 *
 * @author Axon Framework
 * @since 4.12.1
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${axon.axonserver.enabled:true}' == 'false' and '${axon.multi-tenancy.enabled:true}' != 'false'"
)
@AutoConfigureBefore(MultiTenancyAutoConfiguration.class)
public class MultiTenancyLocalSegmentAutoConfiguration {

    private static final String TENANTS_PROPERTY = "axon.multi-tenancy.tenants";
    private static final String AXON_SERVER_CONTEXTS_PROPERTY = "axon.axonserver.contexts";

    @Bean
    @ConditionalOnMissingBean
    public TenantProvider tenantProvider(Environment environment,
                                         TenantConnectPredicate tenantConnectPredicate) {
        String configuredTenants = environment.getProperty(
                TENANTS_PROPERTY,
                environment.getProperty(AXON_SERVER_CONTEXTS_PROPERTY, "")
        );
        List<TenantDescriptor> tenants =
                Arrays.stream(configuredTenants.split(","))
                        .map(String::trim)
                        .filter(tenantId -> !tenantId.isEmpty())
                        .map(TenantDescriptor::tenantWithId)
                        .filter(tenantConnectPredicate)
                        .collect(Collectors.toList());
        return new StaticTenantProvider(tenants);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantCommandSegmentFactory tenantCommandSegmentFactory(
            TransactionManager transactionManager,
            Configuration axonConfiguration,
            DuplicateCommandHandlerResolver duplicateCommandHandlerResolver
    ) {
        return tenant -> {
            SimpleCommandBus commandBus =
                    SimpleCommandBus.builder()
                            .transactionManager(new TenantWrappedTransactionManager(transactionManager, tenant))
                            .duplicateCommandHandlerResolver(duplicateCommandHandlerResolver)
                            .spanFactory(axonConfiguration.getComponent(CommandBusSpanFactory.class))
                            .messageMonitor(axonConfiguration.messageMonitor(
                                    CommandBus.class, "commandBus@" + tenant.tenantId()
                            ))
                            .build();
            commandBus.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
            );
            return commandBus;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantQueryUpdateEmitterSegmentFactory tenantQueryUpdateEmitterSegmentFactory(
            Configuration axonConfiguration
    ) {
        return tenant ->
                SimpleQueryUpdateEmitter.builder()
                        .updateMessageMonitor(axonConfiguration.messageMonitor(
                                QueryUpdateEmitter.class,
                                "queryUpdateEmitter@" + tenant.tenantId()
                        ))
                        .spanFactory(axonConfiguration.getComponent(
                                QueryUpdateEmitterSpanFactory.class
                        ))
                        .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantQuerySegmentFactory tenantQuerySegmentFactory(
            Configuration axonConfiguration,
            TransactionManager transactionManager,
            @Qualifier("multiTenantQueryUpdateEmitter") QueryUpdateEmitter multiTenantQueryUpdateEmitter
    ) {
        return tenant -> SimpleQueryBus.builder()
                .messageMonitor(axonConfiguration.messageMonitor(
                        QueryBus.class, "queryBus@" + tenant.tenantId()
                ))
                .transactionManager(new TenantWrappedTransactionManager(
                        transactionManager, tenant
                ))
                .spanFactory(axonConfiguration.getComponent(QueryBusSpanFactory.class))
                .queryUpdateEmitter(
                        ((MultiTenantQueryUpdateEmitter) multiTenantQueryUpdateEmitter)
                                .getTenant(tenant)
                )
                .errorHandler(axonConfiguration.getComponent(
                        QueryInvocationErrorHandler.class,
                        () -> LoggingQueryInvocationErrorHandler.builder().build()
                ))
                .build();
    }

    @Bean
    @ConditionalOnBean(EventStorageEngine.class)
    @ConditionalOnMissingBean
    public TenantEventSegmentFactory tenantEventSegmentFactory(EventStorageEngine storageEngine,
                                                               Configuration axonConfiguration) {
        return tenant ->
                EmbeddedEventStore.builder()
                        .storageEngine(storageEngine)
                        .messageMonitor(axonConfiguration.messageMonitor(
                                EventStore.class, "eventStore@" + tenant.tenantId()
                        ))
                        .spanFactory(axonConfiguration.getComponent(EventBusSpanFactory.class))
                        .build();
    }
}
