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

import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.springcloud.commandhandling.mode.SerializedMemberCapabilities;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes command capabilities for the requested tenant-specific distributed command bus. */
@RestController
@RequestMapping("${axon.distributed.spring-cloud.rest-mode-url:/member-capabilities}")
@ConditionalOnExpression(
        "'${axon.axonserver.enabled:true}' == 'false' and '${axon.multi-tenancy.enabled:false}' == 'true' " +
                "and '${axon.distributed.enabled:false}' == 'true'"
)
public class TenantMemberCapabilitiesController {

    private final TenantCapabilityDiscoveryModeRegistry discoveryModeRegistry;

    public TenantMemberCapabilitiesController(TenantCapabilityDiscoveryModeRegistry discoveryModeRegistry) {
        this.discoveryModeRegistry = discoveryModeRegistry;
    }

    @GetMapping("/{tenantId}")
    public SerializedMemberCapabilities getLocalMemberCapabilities(@PathVariable String tenantId) {
        return discoveryModeRegistry.discoveryMode(TenantDescriptor.tenantWithId(tenantId))
                                    .getLocalMemberCapabilities();
    }
}
