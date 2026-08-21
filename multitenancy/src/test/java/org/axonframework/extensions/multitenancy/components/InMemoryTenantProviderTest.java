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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryTenantProviderTest {

    private final TenantDescriptor initialTenant = TenantDescriptor.tenantWithId("initial");
    private final TenantDescriptor addedTenant = TenantDescriptor.tenantWithId("added");

    @Test
    void registersInitialAndRuntimeTenantsWithEverySubscriber() {
        MultiTenantAwareComponent firstComponent = mock(MultiTenantAwareComponent.class);
        MultiTenantAwareComponent secondComponent = mock(MultiTenantAwareComponent.class);
        Registration firstInitialRegistration = successfulRegistration();
        Registration secondInitialRegistration = successfulRegistration();
        Registration firstAddedRegistration = successfulRegistration();
        Registration secondAddedRegistration = successfulRegistration();
        when(firstComponent.registerAndStartTenant(initialTenant)).thenReturn(firstInitialRegistration);
        when(secondComponent.registerAndStartTenant(initialTenant)).thenReturn(secondInitialRegistration);
        when(firstComponent.registerAndStartTenant(addedTenant)).thenReturn(firstAddedRegistration);
        when(secondComponent.registerAndStartTenant(addedTenant)).thenReturn(secondAddedRegistration);
        InMemoryTenantProvider testSubject = new InMemoryTenantProvider(singletonList(initialTenant));

        testSubject.subscribe(firstComponent);
        testSubject.subscribe(secondComponent);

        assertTrue(testSubject.registerTenant(addedTenant));
        assertFalse(testSubject.registerTenant(addedTenant));
        assertEquals(asList(initialTenant, addedTenant), testSubject.getTenants());
        verify(firstComponent).registerAndStartTenant(addedTenant);
        verify(secondComponent).registerAndStartTenant(addedTenant);
    }

    @Test
    void rollsBackRuntimeTenantWhenAComponentCannotRegisterIt() {
        MultiTenantAwareComponent firstComponent = mock(MultiTenantAwareComponent.class);
        MultiTenantAwareComponent failingComponent = mock(MultiTenantAwareComponent.class);
        Registration initialRegistration = successfulRegistration();
        Registration failingComponentInitialRegistration = successfulRegistration();
        Registration addedRegistration = successfulRegistration();
        when(firstComponent.registerAndStartTenant(initialTenant)).thenReturn(initialRegistration);
        when(failingComponent.registerAndStartTenant(initialTenant)).thenReturn(failingComponentInitialRegistration);
        when(firstComponent.registerAndStartTenant(addedTenant)).thenReturn(addedRegistration);
        when(failingComponent.registerAndStartTenant(addedTenant)).thenThrow(new IllegalStateException("failed"));
        InMemoryTenantProvider testSubject = new InMemoryTenantProvider(singletonList(initialTenant));
        testSubject.subscribe(firstComponent);
        testSubject.subscribe(failingComponent);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> testSubject.registerTenant(addedTenant));

        assertEquals("failed", exception.getMessage());
        assertEquals(singletonList(initialTenant), testSubject.getTenants());
        verify(addedRegistration).cancel();
    }

    @Test
    void unregistersTenantFromComponentsInReverseSubscriptionOrder() {
        MultiTenantAwareComponent firstComponent = mock(MultiTenantAwareComponent.class);
        MultiTenantAwareComponent secondComponent = mock(MultiTenantAwareComponent.class);
        Registration firstRegistration = successfulRegistration();
        Registration secondRegistration = successfulRegistration();
        when(firstComponent.registerAndStartTenant(initialTenant)).thenReturn(firstRegistration);
        when(secondComponent.registerAndStartTenant(initialTenant)).thenReturn(secondRegistration);
        InMemoryTenantProvider testSubject = new InMemoryTenantProvider(singletonList(initialTenant));
        testSubject.subscribe(firstComponent);
        testSubject.subscribe(secondComponent);

        assertTrue(testSubject.unregisterTenant(initialTenant));

        InOrder cancellationOrder = inOrder(secondRegistration, firstRegistration);
        cancellationOrder.verify(secondRegistration).cancel();
        cancellationOrder.verify(firstRegistration).cancel();
        assertTrue(testSubject.getTenants().isEmpty());
    }

    private static Registration successfulRegistration() {
        Registration registration = mock(Registration.class);
        when(registration.cancel()).thenReturn(true);
        return registration;
    }
}
