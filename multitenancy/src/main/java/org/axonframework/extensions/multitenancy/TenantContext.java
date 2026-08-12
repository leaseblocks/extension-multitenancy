/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extensions.multitenancy;

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.function.Supplier;

/**
 * Holds the tenant that owns work performed outside an Axon {@code UnitOfWork}, such as an HTTP request or a
 * transaction started by a tenant-specific component.
 * <p>
 * The context is thread-bound. Callers should prefer {@link #runWithTenant(TenantDescriptor, Runnable)} or
 * {@link #fetchWithTenant(TenantDescriptor, Supplier)} so the previous context is restored on every exit path.
 *
 * @author Axon Framework
 * @since 4.13.0
 */
public final class TenantContext {

    private static final ThreadLocal<TenantDescriptor> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Returns the tenant associated with the current thread, or {@code null} if none is associated.
     *
     * @return the current tenant
     */
    public static TenantDescriptor currentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Opens a tenant scope that restores the previous tenant when closed. This form is useful for APIs whose work can
     * throw checked exceptions, such as servlet filters.
     *
     * @param tenantDescriptor tenant that owns the work
     * @return the opened scope
     */
    public static Scope openScope(TenantDescriptor tenantDescriptor) {
        return new Scope(setCurrentTenant(tenantDescriptor));
    }

    /**
     * Runs {@code task} with {@code tenantDescriptor} as the current tenant and restores the prior context afterward.
     *
     * @param tenantDescriptor tenant that owns the work
     * @param task             work to execute
     */
    public static void runWithTenant(TenantDescriptor tenantDescriptor, Runnable task) {
        try (Scope ignored = openScope(tenantDescriptor)) {
            task.run();
        }
    }

    /**
     * Invokes {@code supplier} with {@code tenantDescriptor} as the current tenant and restores the prior context
     * afterward.
     *
     * @param tenantDescriptor tenant that owns the work
     * @param supplier         work to execute
     * @param <T>              result type
     * @return the supplier result
     */
    public static <T> T fetchWithTenant(TenantDescriptor tenantDescriptor, Supplier<T> supplier) {
        try (Scope ignored = openScope(tenantDescriptor)) {
            return supplier.get();
        }
    }

    /**
     * A tenant scope. Close it on the same thread on which it was opened.
     */
    public static final class Scope implements AutoCloseable {

        private final TenantDescriptor previousTenant;
        private boolean closed;

        private Scope(TenantDescriptor previousTenant) {
            this.previousTenant = previousTenant;
        }

        @Override
        public void close() {
            if (!closed) {
                restoreCurrentTenant(previousTenant);
                closed = true;
            }
        }
    }

    static TenantDescriptor setCurrentTenant(TenantDescriptor tenantDescriptor) {
        TenantDescriptor previousTenant = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantDescriptor);
        return previousTenant;
    }

    static void restoreCurrentTenant(TenantDescriptor tenantDescriptor) {
        if (tenantDescriptor == null) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantDescriptor);
        }
    }
}
