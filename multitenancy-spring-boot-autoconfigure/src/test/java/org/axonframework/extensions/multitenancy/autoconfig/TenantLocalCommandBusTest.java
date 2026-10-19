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

import java.util.Map;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantLocalCommandBusTest {
    @Test
    @SuppressWarnings("unchecked")
    void rejectsDistributedSegmentWithoutLocalHandlerBusInsteadOfResending() {
        ObjectProvider<MultiTenantCommandBus> provider = mock(ObjectProvider.class);
        MultiTenantCommandBus bus = mock(MultiTenantCommandBus.class);
        DistributedCommandBus segment = mock(DistributedCommandBus.class);
        TenantDescriptor tenant = TenantDescriptor.tenantWithId("alpha");
        when(provider.getObject()).thenReturn(bus);
        when(bus.tenantSegments()).thenReturn(Map.of(tenant, segment));
        when(segment.localSegment()).thenReturn(segment);
        CommandBus receiver = new TenantLocalCommandBus(provider, new MultiTenancyAutoConfiguration().targetTenantResolver());

        assertThrows(IllegalStateException.class, () -> receiver.dispatch(
                new GenericCommandMessage<>("inert", Map.of("tenantId", "alpha"))));

        verify(segment).localSegment();
        verifyNoMoreInteractions(segment);
    }
}
