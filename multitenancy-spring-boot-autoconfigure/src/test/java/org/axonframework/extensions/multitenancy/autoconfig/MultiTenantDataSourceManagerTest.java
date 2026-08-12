/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.springboot.autoconfig.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating Multi-Tenancy auto-configuration for the {@code DataSourceManager}.
 *
 * @author Stefan Dragisic
 */
class MultiTenantDataSourceManagerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            AxonAutoConfiguration.class,
                            EventProcessingAutoConfiguration.class,
                            InfraConfiguration.class,
                            AxonServerBusAutoConfiguration.class,
                            AxonServerAutoConfiguration.class,
                            NoOpTransactionAutoConfiguration.class,
                            ObjectMapperAutoConfiguration.class,
                            TransactionAutoConfiguration.class,
                            XStreamAutoConfiguration.class,
                            MultiTenancyAxonServerAutoConfiguration.class,
                            MultiTenantDataSourceManager.class,
                            AxonTracingAutoConfiguration.class
                    ))
            .withBean(TargetTenantResolver.class,
                      () -> (message, tenants) -> TenantDescriptor.tenantWithId("test"));

    @Test
    void resolveTenantDataSource() {
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.subscribe(any())).thenReturn(() -> true);

        this.contextRunner
                .withPropertyValues(
                        "axon.axonserver.contexts=default",
                        "axon.multi-tenancy.enabled=true"
                )
                .withAllowBeanDefinitionOverriding(true)
                .withBean(TenantProvider.class, () -> tenantProvider)
                .withUserConfiguration(DataSourceResolverConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MultiTenantDataSourceManager.class);
                    MultiTenantDataSourceManager multiTenantDataSourceManager = context.getBean(MultiTenantDataSourceManager.class);
                    verify(tenantProvider).subscribe(multiTenantDataSourceManager);
                    multiTenantDataSourceManager.registerTenant(TenantDescriptor.tenantWithId("test"));
                    assertThat(DataSourceResolverConfiguration.dataSourceResolved.get()).isTrue();
                });
    }

    @Test
    void disabledByDefault() {
        this.contextRunner
                .withUserConfiguration(DataSourcePropertiesResolverConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(MultiTenantDataSourceManager.class));
    }

    static class  DataSourceResolverConfiguration {

        public static AtomicBoolean dataSourceResolved = new AtomicBoolean(false);
        @Bean
        public Function<TenantDescriptor, DataSource> tenantDataSourceResolver() {
            return tenant -> {
                dataSourceResolved.set(true);
                DataSource dataSource = mock(DataSource.class);
                try {
                    when(dataSource.getConnection()).thenReturn(mock(Connection.class));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return dataSource;
            };
        }
    }

    static class DataSourcePropertiesResolverConfiguration {
        @Bean
        public Function<TenantDescriptor, DataSource> tenantDataSourceResolver() {
            return tenant -> mock(DataSource.class);
        }
    }
}
