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

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.junit.jupiter.api.*;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link TenantWrappedTransactionManager}.
 *
 * @author Stefan Dragisic
 */
class TenantWrappedTransactionManagerTest {

    private TransactionManager delegate;
    private final TenantDescriptor tenant1 = TenantDescriptor.tenantWithId("tenant1");

    private TenantWrappedTransactionManager testSubject;

    @BeforeEach
    void setUp() {
        delegate = mock(TransactionManager.class);
        testSubject = new TenantWrappedTransactionManager(delegate, tenant1);
    }

    @Test
    void startTransaction() {
        Transaction transactionMock = mock(Transaction.class);
        when(delegate.startTransaction()).thenReturn(transactionMock);

        Transaction transaction = testSubject.startTransaction();

        assertEquals(tenant1, TenantWrappedTransactionManager.getCurrentTenant());

        transaction.commit();

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).startTransaction();
        verify(transactionMock, times(1)).commit();
    }

    @Test
    void startTransactionRestoresContextOnRollback() {
        TenantDescriptor outerTenant = TenantDescriptor.tenantWithId("outer");
        Transaction transactionMock = mock(Transaction.class);
        when(delegate.startTransaction()).thenReturn(transactionMock);

        TenantContext.runWithTenant(outerTenant, () -> {
            Transaction transaction = testSubject.startTransaction();
            assertEquals(tenant1, TenantContext.currentTenant());

            transaction.rollback();

            assertEquals(outerTenant, TenantContext.currentTenant());
        });

        assertNull(TenantContext.currentTenant());
        verify(transactionMock, times(1)).rollback();
    }

    @Test
    void startTransactionRestoresContextWhenStartingFails() {
        when(delegate.startTransaction()).thenThrow(new IllegalStateException("failure"));

        assertThrows(IllegalStateException.class, testSubject::startTransaction);

        assertNull(TenantContext.currentTenant());
    }

    @Test
    void executeInTransaction() {
        doNothing().when(delegate).executeInTransaction(any());

        Runnable task = () -> assertEquals(tenant1, TenantWrappedTransactionManager.getCurrentTenant());
        testSubject.executeInTransaction(task);

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).executeInTransaction(task);
    }

    @Test
    void executeInTransactionRestoresContextWhenTaskFails() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(delegate).executeInTransaction(any());

        assertThrows(IllegalStateException.class,
                     () -> testSubject.executeInTransaction(() -> {
                         throw new IllegalStateException("failure");
                     }));

        assertNull(TenantContext.currentTenant());
    }

    @Test
    void fetchInTransaction() {
        when(delegate.fetchInTransaction(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());

        Supplier<String> supplier = () -> {
            assertEquals(tenant1, TenantWrappedTransactionManager.getCurrentTenant());
            return "string";
        };
        assertEquals("string", testSubject.fetchInTransaction(supplier));

        assertNull(TenantWrappedTransactionManager.getCurrentTenant());
        verify(delegate, times(1)).fetchInTransaction(supplier);
    }


    @Test
    void nestedContextRestoresOuterTenant() {
        TenantDescriptor outerTenant = TenantDescriptor.tenantWithId("outer");
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(delegate).executeInTransaction(any());

        TenantContext.runWithTenant(outerTenant, () -> {
            testSubject.executeInTransaction(
                    () -> assertEquals(tenant1, TenantContext.currentTenant())
            );
            assertEquals(outerTenant, TenantContext.currentTenant());
        });

        assertNull(TenantContext.currentTenant());
    }
}
