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
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.UnresolvedRoutingKeyPolicy;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.extensions.multitenancy.TenantWrappedTransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.commandhandeling.TenantCommandSegmentFactory;
import org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter;
import org.axonframework.extensions.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.axonframework.extensions.springcloud.commandhandling.mode.AcceptAllCommandsDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.SpanFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Autoconfiguration for tenant-specific Spring Cloud distributed command buses without Axon Server.
 *
 * @author Axon Framework
 * @since 4.12.1
 */
@AutoConfiguration
@ConditionalOnExpression(
        "'${axon.axonserver.enabled:true}' == 'false' and '${axon.multi-tenancy.enabled:false}' == 'true' " +
                "and '${axon.distributed.enabled:false}' == 'true'"
)
@ConditionalOnClass(name = {
        "org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter",
        "org.axonframework.extensions.springcloud.commandhandling.SpringHttpCommandBusConnector",
        "org.springframework.cloud.client.discovery.DiscoveryClient",
        "org.springframework.web.client.RestTemplate"
})
@AutoConfigureBefore(name = {
        "org.axonframework.extensions.springcloud.autoconfig.SpringCloudAutoConfiguration",
        "org.axonframework.extensions.multitenancy.autoconfig.MultiTenancyLocalSegmentAutoConfiguration"
})
@Import(TenantMemberCapabilitiesController.class)
public class MultiTenancySpringCloudAutoConfiguration {

    private static final String SPRING_CLOUD_AUTO_CONFIGURATION =
            "org.axonframework.extensions.springcloud.autoconfig.SpringCloudAutoConfiguration";
    private static final String DISTRIBUTED_COMMAND_BUS_BEAN = "distributedCommandBus";
    private static final String LOAD_FACTOR_PROPERTY = "axon.distributed.load-factor";
    private static final String REST_MODE_URL_PROPERTY = "axon.distributed.spring-cloud.rest-mode-url";
    private static final String REST_MODE_URL_CAMEL_PROPERTY = "axon.distributed.spring-cloud.restModeUrl";
    private static final String CONTEXT_ROOT_PROPERTY =
            "axon.distributed.spring-cloud.context-root-metadata-property-name";
    private static final String ACCEPT_ALL_COMMANDS_PROPERTY =
            "axon.distributed.spring-cloud.enable-accept-all-commands";
    private static final String IGNORE_LISTING_PROPERTY =
            "axon.distributed.spring-cloud.enable-ignore-listing";

    @Bean
    public static BeanDefinitionRegistryPostProcessor springCloudDistributedCommandBusSuppressor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                if (!registry.containsBeanDefinition(DISTRIBUTED_COMMAND_BUS_BEAN)) {
                    return;
                }
                BeanDefinition beanDefinition = registry.getBeanDefinition(DISTRIBUTED_COMMAND_BUS_BEAN);
                if (SPRING_CLOUD_AUTO_CONFIGURATION.equals(beanDefinition.getFactoryBeanName())
                        && DISTRIBUTED_COMMAND_BUS_BEAN.equals(beanDefinition.getFactoryMethodName())) {
                    registry.removeBeanDefinition(DISTRIBUTED_COMMAND_BUS_BEAN);
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                // No-op.
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return AnnotationRoutingStrategy.builder()
                                        .fallbackRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY)
                                        .build();
    }

    @Bean
    public TenantCapabilityDiscoveryModeRegistry tenantCapabilityDiscoveryModeRegistry(
            TenantProvider tenantProvider
    ) {
        TenantCapabilityDiscoveryModeRegistry registry = new TenantCapabilityDiscoveryModeRegistry();
        tenantProvider.subscribe(registry);
        return registry;
    }

    @Bean
    @ConditionalOnBean({DiscoveryClient.class, Registration.class})
    @ConditionalOnMissingBean
    public TenantCommandSegmentFactory tenantSpringCloudCommandSegmentFactory(
            TransactionManager transactionManager,
            Configuration axonConfiguration,
            DuplicateCommandHandlerResolver duplicateCommandHandlerResolver,
            DiscoveryClient discoveryClient,
            Registration localServiceInstance,
            RoutingStrategy routingStrategy,
            @Qualifier("messageSerializer") Serializer messageSerializer,
            RestTemplate restTemplate,
            SpanFactory spanFactory,
            Environment environment,
            TenantCapabilityDiscoveryModeRegistry discoveryModeRegistry
    ) {
        return tenant -> {
            CommandBusSpanFactory commandBusSpanFactory =
                    axonConfiguration.getComponent(CommandBusSpanFactory.class);
            SimpleCommandBus localSegment =
                    SimpleCommandBus.builder()
                                    .transactionManager(new TenantWrappedTransactionManager(
                                            transactionManager, tenant
                                    ))
                                    .duplicateCommandHandlerResolver(duplicateCommandHandlerResolver)
                                    .spanFactory(commandBusSpanFactory)
                                    .messageMonitor(axonConfiguration.messageMonitor(
                                            CommandBus.class, "commandBus@" + tenant.tenantId()
                                    ))
                                    .build();
            localSegment.registerHandlerInterceptor(
                    new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders())
            );

            CapabilityDiscoveryMode capabilityDiscoveryMode =
                    capabilityDiscoveryMode(messageSerializer, restTemplate, environment, tenant,
                                            discoveryModeRegistry);
            SpringCloudCommandRouter commandRouter =
                    SpringCloudCommandRouter.builder()
                                            .discoveryClient(discoveryClient)
                                            .localServiceInstance(localServiceInstance)
                                            .routingStrategy(routingStrategy)
                                            .capabilityDiscoveryMode(capabilityDiscoveryMode)
                                            .serializer(messageSerializer)
                                            .contextRootMetadataPropertyName(
                                                    environment.getProperty(CONTEXT_ROOT_PROPERTY)
                                            )
                                            .build();
            discoveryModeRegistry.registerCommandRouter(tenant, commandRouter);
            SpringHttpCommandBusConnector connector =
                    SpringHttpCommandBusConnector.builder()
                                                 .localCommandBus(localSegment)
                                                 .restOperations(restTemplate)
                                                 .serializer(messageSerializer)
                                                 .spanFactory(spanFactory)
                                                 .build();
            DistributedCommandBus commandBus =
                    DistributedCommandBus.builder()
                                         .commandRouter(commandRouter)
                                         .connector(connector)
                                         .messageMonitor(axonConfiguration.messageMonitor(
                                                 DistributedCommandBus.class,
                                                 "commandBus@" + tenant.tenantId()
                                         ))
                                         .spanFactory(commandBusSpanFactory)
                                         .build();
            commandBus.updateLoadFactor(environment.getProperty(LOAD_FACTOR_PROPERTY, Integer.class, 100));
            return commandBus;
        };
    }

    private CapabilityDiscoveryMode capabilityDiscoveryMode(Serializer serializer,
                                                            RestTemplate restTemplate,
                                                            Environment environment,
                                                            TenantDescriptor tenant,
                                                            TenantCapabilityDiscoveryModeRegistry discoveryModeRegistry) {
        RestCapabilityDiscoveryMode tenantDiscoveryMode =
                RestCapabilityDiscoveryMode.builder()
                                           .serializer(serializer)
                                           .restTemplate(restTemplate)
                                           .messageCapabilitiesEndpoint(restModeUrl(environment) + "/" +
                                                                                tenant.tenantId())
                                           .build();
        discoveryModeRegistry.registerDiscoveryMode(tenant, tenantDiscoveryMode);
        CapabilityDiscoveryMode discoveryMode = tenantDiscoveryMode;
        if (environment.getProperty(ACCEPT_ALL_COMMANDS_PROPERTY, Boolean.class, false)) {
            discoveryMode = AcceptAllCommandsDiscoveryMode.builder()
                                                          .delegate(discoveryMode)
                                                          .build();
        }
        if (environment.getProperty(IGNORE_LISTING_PROPERTY, Boolean.class, true)) {
            discoveryMode = IgnoreListingDiscoveryMode.builder()
                                                      .delegate(discoveryMode)
                                                      .build();
        }
        return discoveryMode;
    }

    private String restModeUrl(Environment environment) {
        return environment.getProperty(
                REST_MODE_URL_PROPERTY,
                environment.getProperty(REST_MODE_URL_CAMEL_PROPERTY, "/member-capabilities")
        );
    }
}
