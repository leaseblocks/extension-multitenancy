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
package org.axonframework.extensions.multitenancy;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Wrapper around the {@link TransactionManager} that adds the current tenant to the transaction context. Used in
 * certain cases to determine the tenant of the currently active transaction, allowing infrastructure components to find
 * the tenant-specific segment.
 *
 * @author Stefan Dragisic
 * @since 4.6.0
 */
public class TenantWrappedTransactionManager implements TransactionManager {

    private final TransactionManager delegate;
    private final TenantDescriptor tenantDescriptor;
    /**
     * Creates a new {@link TenantWrappedTransactionManager} with the given {@code tenantDescriptor}.
     *
     * @param tenantDescriptor The tenant descriptor to be added to the transaction context.
     */
    public TenantWrappedTransactionManager(TenantDescriptor tenantDescriptor) {
        this.delegate = NoTransactionManager.INSTANCE;
        this.tenantDescriptor = tenantDescriptor;
    }

    /**
     * Creates a new {@link TenantWrappedTransactionManager} with the given {@code delegate} and
     * {@code tenantDescriptor}.
     *
     * @param delegate         The delegate transaction manager.
     * @param tenantDescriptor The tenant descriptor to be added to the transaction context.
     */
    public TenantWrappedTransactionManager(TransactionManager delegate,
                                           TenantDescriptor tenantDescriptor) {
        this.delegate = delegate;
        this.tenantDescriptor = tenantDescriptor;
    }

    @Override
    public Transaction startTransaction() {
        TenantDescriptor previousTenant = TenantContext.setCurrentTenant(tenantDescriptor);
        Transaction transaction;
        try {
            transaction = delegate.startTransaction();
        } catch (RuntimeException | Error exception) {
            TenantContext.restoreCurrentTenant(previousTenant);
            throw exception;
        }
        AtomicBoolean completed = new AtomicBoolean();
        return new Transaction() {
            @Override
            public void commit() {
                complete(transaction::commit);
            }

            @Override
            public void rollback() {
                complete(transaction::rollback);
            }

            private void complete(Runnable completion) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    completion.run();
                } finally {
                    TenantContext.restoreCurrentTenant(previousTenant);
                }
            }
        };
    }

    @Override
    public void executeInTransaction(Runnable task) {
        TenantContext.runWithTenant(tenantDescriptor, () -> delegate.executeInTransaction(task));
    }

    @Override
    public <T> T fetchInTransaction(Supplier<T> supplier) {
        return TenantContext.fetchWithTenant(tenantDescriptor, () -> delegate.fetchInTransaction(supplier));
    }

    /**
     * Returns the {@link TenantDescriptor tenant} that's currently active within this thread.
     *
     * @return The {@link TenantDescriptor tenant} that's currently active within this thread.
     */
    public static TenantDescriptor getCurrentTenant() {
        return TenantContext.currentTenant();
    }
}
