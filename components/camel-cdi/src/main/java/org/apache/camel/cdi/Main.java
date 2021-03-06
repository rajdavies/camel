/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.cdi;

import java.util.HashMap;
import java.util.Map;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainSupport;
import org.apache.deltaspike.cdise.api.CdiContainer;
import org.apache.deltaspike.cdise.api.CdiContainerLoader;

/**
 * Camel CDI boot integration. Allows Camel and CDI to be booted up on the command line as a JVM process.
 * See http://camel.apache.org/camel-boot.html.
 */
@Vetoed
public class Main extends MainSupport {

    static {
        // Since version 2.3.0.Final and WELD-1915, Weld SE registers a shutdown hook that conflicts
        // with Camel main support. See WELD-2051. The system property above is available starting
        // Weld 2.3.1.Final to deactivate the registration of the shutdown hook.
        System.setProperty("org.jboss.weld.se.shutdownHook", String.valueOf(Boolean.FALSE));
    }

    private static Main instance;

    private CdiContainer cdiContainer;

    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.run(args);
    }

    /**
     * Returns the currently executing instance.
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        BeanManager manager = cdiContainer.getBeanManager();
        Bean<?> bean = manager.resolve(manager.getBeans(CamelContext.class));
        if (bean == null) {
            throw new UnsatisfiedResolutionException("No default Camel context is deployed, cannot create default ProducerTemplate!");
        }
        CamelContext context = (CamelContext) manager.getReference(bean, CamelContext.class, manager.createCreationalContext(bean));
        return context.createProducerTemplate();
    }

    @Override
    protected Map<String, CamelContext> getCamelContextMap() {
        BeanManager manager = cdiContainer.getBeanManager();
        Map<String, CamelContext> answer = new HashMap<>();
        for (Bean<?> bean : manager.getBeans(CamelContext.class, AnyLiteral.INSTANCE)) {
            CamelContext context = (CamelContext) manager.getReference(bean, CamelContext.class, manager.createCreationalContext(bean));
            answer.put(context.getName(), context);
        }
        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        // TODO: Use standard CDI Java SE support when CDI 2.0 becomes a prerequisite
        CdiContainer container = CdiContainerLoader.getCdiContainer();
        container.boot();
        container.getContextControl().startContexts();
        cdiContainer = container;
        super.doStart();
        postProcessContext();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (cdiContainer != null) {
            cdiContainer.shutdown();
        }
    }
}
