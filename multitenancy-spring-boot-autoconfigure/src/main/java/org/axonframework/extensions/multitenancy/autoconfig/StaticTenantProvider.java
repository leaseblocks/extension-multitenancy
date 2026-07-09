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

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.TenantProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static {@link TenantProvider} implementation for non-Axon-Server deployments.
 *
 * @author Axon Framework
 * @since 4.12.1
 */
public class StaticTenantProvider implements TenantProvider {

    private final List<TenantDescriptor> tenants;

    /**
     * Initializes the provider with the given static {@code tenants}.
     *
     * @param tenants The tenants to register with every subscribed component.
     */
    public StaticTenantProvider(List<TenantDescriptor> tenants) {
        this.tenants = Collections.unmodifiableList(new ArrayList<>(tenants));
    }

    @Override
    public Registration subscribe(MultiTenantAwareComponent component) {
        List<Registration> registrations = new ArrayList<>(tenants.size());
        tenants.forEach(tenant -> registrations.add(component.registerAndStartTenant(tenant)));
        return () -> registrations.stream()
                                  .map(Registration::cancel)
                                  .reduce((previous, current) -> previous && current)
                                  .orElse(false);
    }

    @Override
    public List<TenantDescriptor> getTenants() {
        return tenants;
    }
}
