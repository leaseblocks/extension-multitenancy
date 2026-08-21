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

import org.axonframework.common.Registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Thread-safe, in-memory {@link TenantRegistry}. Tenant changes are applied to every subscribed component before the
 * registry exposes the changed tenant set. A failed component registration rolls back registrations completed for
 * that tenant during the same operation.
 *
 * @author Axon Framework
 * @since 4.13.0
 */
public class InMemoryTenantProvider implements TenantRegistry {

    private final Set<TenantDescriptor> tenants;
    private final Map<MultiTenantAwareComponent, Map<TenantDescriptor, Registration>> componentRegistrations =
            new LinkedHashMap<>();

    /**
     * Initializes the provider with the given tenants.
     *
     * @param tenants The tenants initially known by this provider.
     */
    public InMemoryTenantProvider(List<TenantDescriptor> tenants) {
        this.tenants = new LinkedHashSet<>(Objects.requireNonNull(tenants, "tenants"));
    }

    @Override
    public synchronized Registration subscribe(MultiTenantAwareComponent component) {
        Objects.requireNonNull(component, "component");
        if (componentRegistrations.containsKey(component)) {
            throw new IllegalArgumentException("The component is already subscribed to this tenant provider");
        }

        Map<TenantDescriptor, Registration> registrations = new LinkedHashMap<>();
        try {
            for (TenantDescriptor tenant : tenants) {
                registrations.put(tenant, requireRegistration(component.registerAndStartTenant(tenant)));
            }
        } catch (RuntimeException | Error failure) {
            cancelInReverseOrder(registrations.values(), failure);
            throw failure;
        }
        componentRegistrations.put(component, registrations);
        return () -> unsubscribe(component);
    }

    @Override
    public synchronized boolean registerTenant(TenantDescriptor tenantDescriptor) {
        Objects.requireNonNull(tenantDescriptor, "tenantDescriptor");
        if (tenants.contains(tenantDescriptor)) {
            return false;
        }

        Map<MultiTenantAwareComponent, Registration> addedRegistrations = new LinkedHashMap<>();
        try {
            for (MultiTenantAwareComponent component : componentRegistrations.keySet()) {
                Registration registration = requireRegistration(component.registerAndStartTenant(tenantDescriptor));
                componentRegistrations.get(component).put(tenantDescriptor, registration);
                addedRegistrations.put(component, registration);
            }
        } catch (RuntimeException | Error failure) {
            rollbackTenantRegistration(tenantDescriptor, addedRegistrations, failure);
            throw failure;
        }
        tenants.add(tenantDescriptor);
        return true;
    }

    @Override
    public synchronized boolean unregisterTenant(TenantDescriptor tenantDescriptor) {
        Objects.requireNonNull(tenantDescriptor, "tenantDescriptor");
        if (!tenants.remove(tenantDescriptor)) {
            return false;
        }

        boolean cancelled = true;
        List<Map<TenantDescriptor, Registration>> registrations =
                new ArrayList<>(componentRegistrations.values());
        Collections.reverse(registrations);
        for (Map<TenantDescriptor, Registration> componentRegistration : registrations) {
            Registration registration = componentRegistration.remove(tenantDescriptor);
            if (registration != null) {
                cancelled = registration.cancel() && cancelled;
            }
        }
        return cancelled;
    }

    @Override
    public synchronized List<TenantDescriptor> getTenants() {
        return Collections.unmodifiableList(new ArrayList<>(tenants));
    }

    private synchronized boolean unsubscribe(MultiTenantAwareComponent component) {
        Map<TenantDescriptor, Registration> registrations = componentRegistrations.remove(component);
        return registrations != null && cancelInReverseOrder(registrations.values(), null);
    }

    private void rollbackTenantRegistration(TenantDescriptor tenantDescriptor,
                                            Map<MultiTenantAwareComponent, Registration> addedRegistrations,
                                            Throwable failure) {
        List<Map.Entry<MultiTenantAwareComponent, Registration>> entries =
                new ArrayList<>(addedRegistrations.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<MultiTenantAwareComponent, Registration> entry : entries) {
            try {
                entry.getValue().cancel();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            } finally {
                componentRegistrations.get(entry.getKey()).remove(tenantDescriptor);
            }
        }
    }

    private static Registration requireRegistration(Registration registration) {
        return Objects.requireNonNull(registration, "A multi-tenant component returned a null registration");
    }

    private static boolean cancelInReverseOrder(Iterable<Registration> registrations, Throwable failure) {
        List<Registration> registrationsInReverse = new ArrayList<>();
        registrations.forEach(registrationsInReverse::add);
        Collections.reverse(registrationsInReverse);

        boolean cancelled = true;
        for (Registration registration : registrationsInReverse) {
            try {
                cancelled = registration.cancel() && cancelled;
            } catch (RuntimeException cancellationFailure) {
                cancelled = false;
                if (failure != null) {
                    failure.addSuppressed(cancellationFailure);
                }
            }
        }
        return cancelled;
    }
}
