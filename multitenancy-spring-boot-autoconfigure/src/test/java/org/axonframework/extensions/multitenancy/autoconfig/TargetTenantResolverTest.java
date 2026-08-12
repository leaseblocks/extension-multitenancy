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
package org.axonframework.extensions.multitenancy.autoconfig;

import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.axonframework.extensions.multitenancy.autoconfig.TenantConfiguration.TENANT_CORRELATION_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetTenantResolverTest {

    private static final TenantDescriptor TENANT = TenantDescriptor.tenantWithId("tenant-1");
    private final TargetTenantResolver<Message<?>> resolver =
            new MultiTenancyAutoConfiguration().targetTenantResolver();

    @Test
    void resolvesRegisteredTenant() {
        Message<String> message = new GenericMessage<>(
                "payload", Map.of(TENANT_CORRELATION_KEY, TENANT.tenantId())
        );

        assertEquals(TENANT, resolver.resolveTenant(message, Set.of(TENANT)));
    }

    @Test
    void rejectsMessageWithoutTenant() {
        Message<String> message = new GenericMessage<>("payload");

        assertThrows(NoSuchTenantException.class, () -> resolver.resolveTenant(message, Set.of(TENANT)));
    }

    @Test
    void rejectsUnregisteredTenant() {
        Message<String> message = new GenericMessage<>(
                "payload", Map.of(TENANT_CORRELATION_KEY, "tenant-2")
        );

        assertThrows(NoSuchTenantException.class, () -> resolver.resolveTenant(message, Set.of(TENANT)));
    }
}
