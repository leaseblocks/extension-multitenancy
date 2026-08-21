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

import org.axonframework.extensions.multitenancy.components.InMemoryTenantProvider;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.List;

/**
 * Default in-memory tenant provider for non-Axon-Server deployments. The tenants supplied to the constructor are the
 * initial set; additional tenants can be registered at runtime through the inherited tenant registry API.
 *
 * @author Axon Framework
 * @since 4.12.1
 */
public class StaticTenantProvider extends InMemoryTenantProvider {

    /**
     * Initializes the provider with the given static {@code tenants}.
     *
     * @param tenants The tenants to register with every subscribed component.
     */
    public StaticTenantProvider(List<TenantDescriptor> tenants) {
        super(tenants);
    }
}
