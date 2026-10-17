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
package org.axonframework.extensions.multitenancy.integration;

import org.axonframework.extensions.multitenancy.autoconfig.TenantCapabilityDiscoveryModeRegistry;
import org.axonframework.extensions.multitenancy.autoconfig.TenantMemberCapabilitiesController;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.mode.SerializedMemberCapabilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantMemberCapabilitiesMvcTest {

    @ParameterizedTest
    @ValueSource(strings = {"/member-capabilities", "/custom-capabilities"})
    void pathVariableSelectsTheRequestedTenantCapabilities(String endpoint) throws Exception {
        TenantCapabilityDiscoveryModeRegistry registry = new TenantCapabilityDiscoveryModeRegistry();
        registerCapabilities(registry, "tenant-one", 101);
        registerCapabilities(registry, "tenant-two", 202);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TenantMemberCapabilitiesController(registry))
                                     .addPlaceholderValue("axon.distributed.spring-cloud.rest-mode-url", endpoint)
                                     .build();

        mvc.perform(get(endpoint + "/tenant-one"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.loadFactor").value(101));
        mvc.perform(get(endpoint + "/tenant-two"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.loadFactor").value(202));
    }

    private void registerCapabilities(TenantCapabilityDiscoveryModeRegistry registry, String tenantId, int loadFactor) {
        RestCapabilityDiscoveryMode discoveryMode = mock(RestCapabilityDiscoveryMode.class);
        when(discoveryMode.getLocalMemberCapabilities()).thenReturn(
                new SerializedMemberCapabilities(loadFactor, "filter", "filter-type")
        );
        registry.registerDiscoveryMode(TenantDescriptor.tenantWithId(tenantId), discoveryMode);
    }
}
