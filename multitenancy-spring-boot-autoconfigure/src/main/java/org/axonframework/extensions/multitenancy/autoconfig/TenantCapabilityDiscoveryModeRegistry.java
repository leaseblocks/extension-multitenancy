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

import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.MultiTenantAwareComponent;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode;
import org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maintains the REST capability discovery mode for every Spring Cloud tenant command segment. */
public class TenantCapabilityDiscoveryModeRegistry
        implements MultiTenantAwareComponent, ApplicationListener<HeartbeatEvent> {

    private final Map<TenantDescriptor, RestCapabilityDiscoveryMode> discoveryModes = new ConcurrentHashMap<>();
    private final Map<TenantDescriptor, SpringCloudCommandRouter> commandRouters = new ConcurrentHashMap<>();

    public void registerDiscoveryMode(TenantDescriptor tenantDescriptor,
                                      RestCapabilityDiscoveryMode discoveryMode) {
        discoveryModes.put(tenantDescriptor, discoveryMode);
    }

    public RestCapabilityDiscoveryMode discoveryMode(TenantDescriptor tenantDescriptor) {
        RestCapabilityDiscoveryMode discoveryMode = discoveryModes.get(tenantDescriptor);
        if (discoveryMode == null) {
            throw new NoSuchTenantException(tenantDescriptor.tenantId());
        }
        return discoveryMode;
    }

    public void registerCommandRouter(TenantDescriptor tenantDescriptor, SpringCloudCommandRouter commandRouter) {
        commandRouters.put(tenantDescriptor, commandRouter);
    }

    @Override
    public void onApplicationEvent(HeartbeatEvent event) {
        commandRouters.values().forEach(commandRouter -> commandRouter.updateMemberships(event));
    }

    @Override
    public Registration registerTenant(TenantDescriptor tenantDescriptor) {
        return () -> {
            commandRouters.remove(tenantDescriptor);
            return discoveryModes.remove(tenantDescriptor) != null;
        };
    }

    @Override
    public Registration registerAndStartTenant(TenantDescriptor tenantDescriptor) {
        return registerTenant(tenantDescriptor);
    }
}
