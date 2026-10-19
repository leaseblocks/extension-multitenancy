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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.extensions.multitenancy.components.NoSuchTenantException;
import org.axonframework.extensions.multitenancy.components.TargetTenantResolver;
import org.axonframework.extensions.multitenancy.components.TenantDescriptor;
import org.axonframework.extensions.multitenancy.components.commandhandeling.MultiTenantCommandBus;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.springframework.beans.factory.ObjectProvider;

final class TenantLocalCommandBus implements CommandBus {
    private final ObjectProvider<MultiTenantCommandBus> commandBus;
    private final TargetTenantResolver<CommandMessage<?>> targetTenantResolver;

    @SuppressWarnings("unchecked")
    TenantLocalCommandBus(ObjectProvider<MultiTenantCommandBus> commandBus,
                          TargetTenantResolver<?> targetTenantResolver) {
        this.commandBus = commandBus;
        this.targetTenantResolver = (TargetTenantResolver<CommandMessage<?>>) targetTenantResolver;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        localSegment(command).dispatch(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        CommandBus local;
        try {
            local = localSegment(command);
        } catch (NoSuchTenantException | IllegalStateException exception) {
            callback.onResult(command, GenericCommandResultMessage.asCommandResultMessage(exception));
            return;
        }
        local.dispatch(command, callback);
    }

    private CommandBus localSegment(CommandMessage<?> command) {
        MultiTenantCommandBus bus = commandBus.getObject();
        TenantDescriptor tenant = targetTenantResolver.resolveTenant(command, bus.tenantSegments().keySet());
        CommandBus segment = bus.tenantSegments().get(tenant);
        if (segment == null) {
            throw new NoSuchTenantException(tenant.tenantId());
        }
        if (!(segment instanceof DistributedCommandBus)) {
            throw new IllegalStateException("Tenant receive segment must be a distributed command bus");
        }
        CommandBus local = ((DistributedCommandBus) segment).localSegment();
        if (local == segment) {
            throw new IllegalStateException("Tenant command bus has no local receive segment");
        }
        return local;
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return commandBus.getObject().subscribe(commandName, handler);
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super CommandMessage<?>> interceptor) {
        return commandBus.getObject().registerDispatchInterceptor(interceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> interceptor) {
        return commandBus.getObject().registerHandlerInterceptor(interceptor);
    }
}
