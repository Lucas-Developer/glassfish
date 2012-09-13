/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.connectors.deployment.annotation.handlers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import javax.jms.JMSConnectionFactoryDefinition;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.Interceptors;

import org.glassfish.apf.AnnotationHandlerFor;
import org.glassfish.apf.AnnotationInfo;
import org.glassfish.apf.AnnotationProcessorException;
import org.glassfish.apf.HandlerProcessingResult;
import org.glassfish.deployment.common.RootDeploymentDescriptor;
import org.jvnet.hk2.annotations.Service;

import com.sun.enterprise.deployment.EjbBundleDescriptor;
import com.sun.enterprise.deployment.EjbDescriptor;
import com.sun.enterprise.deployment.JMSConnectionFactoryDefinitionDescriptor;
import com.sun.enterprise.deployment.MetadataSource;
import com.sun.enterprise.deployment.WebBundleDescriptor;
import com.sun.enterprise.deployment.annotation.context.EjbBundleContext;
import com.sun.enterprise.deployment.annotation.context.EjbContext;
import com.sun.enterprise.deployment.annotation.context.EjbInterceptorContext;
import com.sun.enterprise.deployment.annotation.context.ResourceContainerContext;
import com.sun.enterprise.deployment.annotation.context.WebBundleContext;
import com.sun.enterprise.deployment.annotation.context.WebComponentContext;
import com.sun.enterprise.deployment.annotation.context.WebComponentsContext;
import com.sun.enterprise.deployment.annotation.handlers.AbstractResourceHandler;

@Service
@AnnotationHandlerFor(JMSConnectionFactoryDefinition.class)
public class JMSConnectionFactoryDefinitionHandler extends AbstractResourceHandler {

    public JMSConnectionFactoryDefinitionHandler() {
    }

    protected HandlerProcessingResult processAnnotation(AnnotationInfo ainfo, ResourceContainerContext[] rcContexts)
            throws AnnotationProcessorException {
    	JMSConnectionFactoryDefinition jmsConnectionFactoryDefnAn =
                (JMSConnectionFactoryDefinition)ainfo.getAnnotation();
        return processAnnotation(jmsConnectionFactoryDefnAn, ainfo, rcContexts);
    }

    protected HandlerProcessingResult processAnnotation(JMSConnectionFactoryDefinition jmsConnectionFactoryDefnAn, AnnotationInfo aiInfo,
                                                        ResourceContainerContext[] rcContexts)
            throws AnnotationProcessorException {
        Class<?> annotatedClass = (Class<?>)aiInfo.getAnnotatedElement();
        Annotation[] annotations = annotatedClass.getAnnotations();
        boolean warClass = isAWebComponentClass(annotations);
        boolean ejbClass = isAEjbComponentClass(annotations);

        for (ResourceContainerContext context : rcContexts) {
            if (!canProcessAnnotation(annotatedClass, ejbClass, warClass, context)) {
                return getDefaultProcessedResult();
            }

            Set<JMSConnectionFactoryDefinitionDescriptor> jmscfdDescs = context.getJMSConnectionFactoryDefinitionDescriptors();
            JMSConnectionFactoryDefinitionDescriptor desc = createDescriptor(jmsConnectionFactoryDefnAn);
            if (isDefinitionAlreadyPresent(jmscfdDescs, desc)) {
                merge(jmscfdDescs, jmsConnectionFactoryDefnAn);
            } else {
                context.addJMSConnectionFactoryDefinitionDescriptor(desc);
            }
        }
        return getDefaultProcessedResult();
    }

    /**
     * To take care of the case where an ejb is provided in a .war and
     * annotation processor will process this class twice (once for ejb and
     * once for web-bundle-context, which is a bug).<br>
     * This method helps to overcome the issue, partially.<br>
     * Checks whether both the annotated class and the context are either ejb or web.
     *
     * @param annotatedClass annotated-class
     * @param ejbClass indicates whether the class is an ejb-class
     * @param warClass indicates whether the class is an web-class
     * @param context resource-container-context
     * @return boolean indicates whether the annotation can be processed.
     */
    private boolean canProcessAnnotation(Class<?> annotatedClass, boolean ejbClass, boolean warClass,
                                         ResourceContainerContext context) {
        if (ejbClass) {
            if (!(context instanceof EjbBundleContext ||
                    context instanceof EjbContext ||
                    context instanceof EjbInterceptorContext
            )) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing as the class is " +
                            "an EJB class and context is not one of EJBContext");
                }
                return false;
            }
        } else if (context instanceof EjbBundleContext) {
            EjbBundleContext ejbContext = (EjbBundleContext) context;
            EjbBundleDescriptor ejbBundleDescriptor = ejbContext.getDescriptor();
            EjbDescriptor[] ejbDescriptor = ejbBundleDescriptor.getEjbByClassName(annotatedClass.getName());
            if (ejbDescriptor == null || ejbDescriptor.length == 0) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing as the class " +
                            "[ " + annotatedClass + " ] is " +
                            "not an EJB class and the context is EJBContext");
                }
                return false;
            }
        } else if (warClass) {
            if (!(context instanceof WebBundleContext || context instanceof WebComponentsContext
                    || context instanceof WebComponentContext )) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing as the class is " +
                            "an Web class and context is not one of WebContext");
                }
                return false;
            }
        } else if (context instanceof WebBundleContext) {
            WebBundleContext webBundleContext = (WebBundleContext) context;
            WebBundleDescriptor webBundleDescriptor = webBundleContext.getDescriptor();
            Collection<RootDeploymentDescriptor> extDesc = webBundleDescriptor.getExtensionsDescriptors();
            for (RootDeploymentDescriptor desc : extDesc) {
                if (desc instanceof EjbBundleDescriptor) {
                    EjbBundleDescriptor ejbBundleDesc = (EjbBundleDescriptor)desc;
                    EjbDescriptor[] ejbDescs = ejbBundleDesc.getEjbByClassName(annotatedClass.getName());
                    if (ejbDescs != null && ejbDescs.length > 0) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing as the class " +
                                    "[ " + annotatedClass + " ] is " +
                                    "not an Web class and the context is WebContext");
                        }
                        return false;
                    } else if (ejbBundleDesc.getInterceptorByClassName(annotatedClass.getName()) != null) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing " +
                                        "as the class " +
                                        "[ " + annotatedClass + " ] is " +
                                        "not an Web class and the context is WebContext");
                            }
                            return false;
                    } else {
                        Method[] methods = annotatedClass.getDeclaredMethods();
                        for (Method method : methods) {
                            Annotation annotations[] = method.getAnnotations();
                            for (Annotation annotation : annotations) {
                                if (annotation.annotationType().equals(AroundInvoke.class) ||
                                        annotation.annotationType().equals(AroundTimeout.class) ||
                                        annotation.annotationType().equals(Interceptors.class)) {
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "Ignoring @JMSConnectionFactoryDefinition annotation processing " +
                                                "as the class " +
                                                "[ " + annotatedClass + " ] is " +
                                                "not an Web class, an interceptor and the context is WebContext");
                                    }
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean isDefinitionAlreadyPresent(Set<JMSConnectionFactoryDefinitionDescriptor> jmscfdDescs,
                                               JMSConnectionFactoryDefinitionDescriptor desc) {
        for (JMSConnectionFactoryDefinitionDescriptor jmscfdDesc : jmscfdDescs) {
            if (jmscfdDesc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    private void merge(Set<JMSConnectionFactoryDefinitionDescriptor> jmscfdDescs, JMSConnectionFactoryDefinition defn) {

        for (JMSConnectionFactoryDefinitionDescriptor desc : jmscfdDescs) {

            if (desc.getName().equals(defn.name())) {

                if (desc.getClassName() == null) {
                    desc.setClassName(defn.className());
                }

                if (desc.getDescription() == null) {
                    if (defn.description() != null && !defn.description().equals("")) {
                        desc.setDescription(defn.description());
                    }
                }

                if (desc.getResourceAdapterName() == null) {
                    if (defn.resourceAdapterName() != null && !defn.resourceAdapterName().equals("")) {
                        desc.setResourceAdapterName(defn.resourceAdapterName());
                    }
                }

                if (desc.getUser() == null) {
                    if (defn.user() != null && !defn.user().equals("")) {
                        desc.setUser(defn.user());
                    }
                }

                if (desc.getPassword() == null) {
                    if (defn.password() != null /*ALLOW EMPTY PASSWORDS && !defn.password().equals("")*/) {
                        desc.setPassword(defn.password());
                    }
                }

                if (desc.getClientId() == null) {
                    if (defn.clientId() != null && !defn.clientId().equals("")) {
                        desc.setClientId(defn.clientId());
                    }
                }

                if (!desc.isConnectionTimeoutSet()) {
                    if (defn.connectionTimeout() >= 0) {
                        desc.setConnectionTimeout(defn.connectionTimeout());
                    }
                }

                if (!desc.isTransactionSet()) {
                    desc.setTransactional(defn.transactional());
                }

                if (desc.getInitialPoolSize() < 0) {
                    if (defn.initialPoolSize() >= 0) {
                        desc.setInitialPoolSize(defn.initialPoolSize());
                    }
                }

                if (desc.getMaxPoolSize() < 0) {
                    if (defn.maxPoolSize() >= 0) {
                        desc.setMaxPoolSize(defn.maxPoolSize());
                    }
                }

                if (desc.getMinPoolSize() < 0) {
                    if (defn.minPoolSize() >= 0) {
                        desc.setMinPoolSize(defn.minPoolSize());
                    }
                }

                if (desc.getMaxIdleTime() < 0) {
                    if (defn.maxIdleTime() >= 0) {
                        desc.setMaxIdleTime(defn.maxIdleTime());
                    }
                }

                Properties properties = desc.getProperties();
                String[] defnProperties = defn.properties();

                if (defnProperties.length > 0) {
                    for (String property : defnProperties) {
                        int index = property.indexOf("=");
                        // found "=" and not at start or end of string
                        if (index > 0 && index < property.length() - 1) {
                            String name = property.substring(0, index).trim();
                            String value = property.substring(index + 1).trim();
                            //add to properties only when not already present
                            if (properties.get(name) == null) {
                                properties.put(name, value);
                            }
                        }
                    }
                }
                break;
            }
        }

    }

    private JMSConnectionFactoryDefinitionDescriptor createDescriptor(JMSConnectionFactoryDefinition defn) {

    	JMSConnectionFactoryDefinitionDescriptor desc = new JMSConnectionFactoryDefinitionDescriptor();
        desc.setMetadataSource(MetadataSource.ANNOTATION);

        desc.setName(defn.name());
        desc.setClassName(defn.className());

        if (defn.description() != null && !defn.description().equals("")) {
            desc.setDescription(defn.description());
        }

        if (defn.resourceAdapterName() != null && !defn.resourceAdapterName().equals("")) {
            desc.setResourceAdapterName(defn.resourceAdapterName());
        }

        if (defn.user() != null && !defn.user().equals("")) {
            desc.setUser(defn.user());
        }

        if (defn.password() != null /*ALLOW EMPTY PASSWORDS && !defn.password().equals("")*/) {
            desc.setPassword(defn.password());
        }

        if (defn.clientId() != null && !defn.clientId().equals("")) {
            desc.setClientId(defn.clientId());
        }

        if (defn.connectionTimeout() >= 0) {
            desc.setConnectionTimeout(defn.connectionTimeout());
        }

        desc.setTransactional(defn.transactional());

        if (defn.initialPoolSize() >= 0) {
            desc.setInitialPoolSize(defn.initialPoolSize());
        }
        if (defn.maxPoolSize() >= 0) {
            desc.setMaxPoolSize(defn.maxPoolSize());
        }
        if (defn.minPoolSize() >= 0) {
            desc.setMinPoolSize(defn.minPoolSize());
        }
        if (defn.maxIdleTime() >= 0) {
            desc.setMaxIdleTime(defn.maxIdleTime());
        }

        if (defn.properties() != null) {
            Properties properties = desc.getProperties();

            String[] defnProperties = defn.properties();
            if (defnProperties.length > 0) {
                for (String property : defnProperties) {
                    int index = property.indexOf("=");
                    // found "=" and not at start or end of string
                    if (index > 0 && index < property.length() - 1) {
                        String name = property.substring(0, index).trim();
                        String value = property.substring(index + 1).trim();
                        properties.put(name, value);
                    }
                }
            }
        }

        return desc;
    }
}

