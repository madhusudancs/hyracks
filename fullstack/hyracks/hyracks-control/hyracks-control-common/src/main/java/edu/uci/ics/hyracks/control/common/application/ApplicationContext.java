/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.common.application;

import java.io.IOException;
import java.io.Serializable;

import edu.uci.ics.hyracks.api.application.IApplicationContext;
import edu.uci.ics.hyracks.api.messages.IMessageBroker;
import edu.uci.ics.hyracks.control.common.context.ServerContext;

public abstract class ApplicationContext implements IApplicationContext {
    protected ServerContext serverCtx;
    protected Serializable distributedState;
    protected IMessageBroker messageBroker;

    public ApplicationContext(ServerContext serverCtx) throws IOException {
        this.serverCtx = serverCtx;
    }

    @Override
    public Serializable getDistributedState() {
        return distributedState;
    }

    @Override
    public void setMessageBroker(IMessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    @Override
    public IMessageBroker getMessageBroker() {
        return this.messageBroker;
    }
}