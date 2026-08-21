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
package org.axonframework.extensions.multitenancy.components;

/**
 * A {@link TenantProvider} that can add and remove tenants while the application is running.
 *
 * @author Axon Framework
 * @since 4.13.0
 */
public interface TenantRegistry extends TenantProvider {

    /**
     * Registers and starts the given tenant on every subscribed multi-tenant component.
     *
     * @param tenantDescriptor The tenant to register.
     * @return {@code true} when the tenant was added, or {@code false} when it was already registered.
     */
    boolean registerTenant(TenantDescriptor tenantDescriptor);

    /**
     * Stops and removes the given tenant from every subscribed multi-tenant component.
     *
     * @param tenantDescriptor The tenant to remove.
     * @return {@code true} when the tenant existed and every component registration was cancelled successfully.
     */
    boolean unregisterTenant(TenantDescriptor tenantDescriptor);
}
