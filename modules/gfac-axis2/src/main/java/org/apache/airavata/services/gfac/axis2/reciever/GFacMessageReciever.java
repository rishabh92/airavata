package org.apache.airavata.services.gfac.axis2.reciever;

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.airavata.commons.gfac.api.Registry;
import org.apache.airavata.commons.gfac.api.impl.JCRRegistry;
import org.apache.airavata.commons.gfac.type.Parameter;
import org.apache.airavata.commons.gfac.type.ServiceDescription;
import org.apache.airavata.commons.gfac.type.parameter.AbstractParameter;
import org.apache.airavata.commons.gfac.type.util.SchemaUtil;
import org.apache.airavata.core.gfac.context.invocation.impl.DefaultExecutionContext;
import org.apache.airavata.core.gfac.context.invocation.impl.DefaultInvocationContext;
import org.apache.airavata.core.gfac.context.message.impl.ParameterContextImpl;
import org.apache.airavata.core.gfac.factory.PropertyServiceFactory;
import org.apache.airavata.core.gfac.notification.impl.LoggingNotification;
import org.apache.airavata.core.gfac.services.GenericService;
import org.apache.airavata.services.gfac.axis2.utils.GFacServiceOperations;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.util.MessageContextBuilder;
import org.apache.axis2.util.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.InputSource;

public class GFacMessageReciever implements MessageReceiver {

    private static final Log log = LogFactory.getLog(GFacMessageReciever.class);
    public static final String SECURITY_CONTEXT = "security_context";
    GenericService service = null;

    public void receive(org.apache.axis2.context.MessageContext axisRequestMsgCtx) throws AxisFault {
        GFacServiceOperations operation = GFacServiceOperations.valueFrom(axisRequestMsgCtx.getOperationContext()
                .getOperationName());
        switch (operation) {
        case GETABSTRACTWSDL:
            try {
                processgetAbstractWSDLOperation(axisRequestMsgCtx);
            } catch (Exception e) {
                throw new AxisFault("Error retrieving the WSDL", e);
            }
            log.info("getAbstractWSDL operation invoked !!");
            break;
        case INVOKE:
            try {
                processInvokeOperation(axisRequestMsgCtx);
                log.info("Invoke operation invoked !!");
            } catch (Exception e) {
                throw new AxisFault("Error Invoking the service", e);
            }
            break;
        case GETWSDL:
            try {
                processgetWSDLOperation(axisRequestMsgCtx);
            } catch (Exception e) {
                throw new AxisFault("Error retrieving the WSDL", e);
            }
            log.info("getWSDL operation invoked !!");
            break;
        }
    }

    public void processInvokeOperation(MessageContext messageContext) throws Exception {
        MessageContext response = null;
        String serviceName = getOriginalServiceName(messageContext);
        try {
            ConfigurationContext context = messageContext.getConfigurationContext();
            
            /*
             * We assume that input likes
             * <invoke>
             *  <input_param_name1>value</input_param_name1>
             *  <input_param_name2>value</input_param_name2>
             *  <input_param_name3>value</input_param_name3>
             * </invoke>
             */
            OMElement invoke = messageContext.getEnvelope().getBody().getFirstElement();
            
            /*
             * We assume that output likes
             * <invokeResponse>
             *  <output_param_name1>value</output_param_name1>
             *  <output_param_name2>value</output_param_name2>
             *  <output_param_name3>value</output_param_name3>
             * </invokeResponse>
             */            
            OMElement output = invokeApplication(serviceName, invoke, context);

            SOAPFactory sf = OMAbstractFactory.getSOAP11Factory();
            SOAPEnvelope responseEnv = sf.createSOAPEnvelope();
            sf.createSOAPBody(responseEnv);
            responseEnv.getBody().addChild(output);
            response = MessageContextBuilder.createOutMessageContext(messageContext);
            response.setEnvelope(responseEnv);
            response.getOperationContext().addMessageContext(response);
            AxisEngine.send(response);
        } catch (Exception e) {
            throw e;
        }
    }

    private OMElement invokeApplication(String serviceName, OMElement input, ConfigurationContext context)
            throws Exception {
        OMElement outputElement = null;
        try {
            Repository repository = (Repository) context.getProperty("repository");
            Credentials credentials = (Credentials) context.getProperty("credentials");

            Registry regis = new JCRRegistry(repository, credentials);
            LoggingNotification notification = new LoggingNotification();

            DefaultInvocationContext ct = new DefaultInvocationContext();
            ct.setExecutionContext(new DefaultExecutionContext());
            ct.setServiceName(serviceName);
            ct.getExecutionContext().setRegistryService(regis);
            ct.getExecutionContext().setNotificationService(notification);
            

            /*
             * read from registry and set the correct parameters
             */
            ServiceDescription serviceDescription = regis.getServiceDescription(serviceName);

            /*
             * Input
             */
            ParameterContextImpl inputParam = new ParameterContextImpl();
            List<Parameter> inputs = serviceDescription.getInputParameters();
            for (Parameter parameter : inputs) {
                OMElement element = input.getFirstChildWithName(new QName(parameter.getName()));

                if (element == null) {
                    throw new Exception("Parameter is not found in the message");
                }

                AbstractParameter param = SchemaUtil.mapFromType(parameter.getType());
                param.parseStringVal(element.getText());
                inputParam.add(parameter.getName(), param);
            }

            /*
             * Output
             */
            ParameterContextImpl outputParam = new ParameterContextImpl();
            List<Parameter> outputs = serviceDescription.getOutputParameters();
            for (Parameter parameter : outputs) {
                outputParam.add(parameter.getName(), SchemaUtil.mapFromType(parameter.getType()));
            }

            ct.addMessageContext(ParameterContextImpl.INPUT_KEY, inputParam);
            ct.addMessageContext(ParameterContextImpl.OUTPUT_KEY, outputParam);

            if (service == null) {
                service = new PropertyServiceFactory().createService();
            }
            service.init();
            // invoke service
            service.execute(ct);

            /*
             * Process Output
             */
            OMFactory fac = OMAbstractFactory.getOMFactory();
            OMNamespace omNs = fac.createOMNamespace("http://ws.apache.org/axis2/xsd", "ns1");
            outputElement = fac.createOMElement("invokeResponse", omNs);

            ParameterContextImpl paramContext = (ParameterContextImpl) ct.<AbstractParameter> getMessageContext("output");                      
            for (Iterator<String> iterator = paramContext.getNames(); iterator.hasNext();) {
                String name = iterator.next();
                OMElement ele = fac.createOMElement(name, omNs);
                ele.addAttribute("type", paramContext.getValue(name).getType().toString(), omNs);
                ele.setText(paramContext.getValue(name).toStringVal());
                outputElement.addChild(ele);
            }

        } catch (Exception e) {
            log.error("Error in invoking service", e);
            throw e;
        }
        return outputElement;
    }

    public void processgetWSDLOperation(MessageContext messageContext) throws Exception {
        MessageContext response = null;
        String serviceName = getOriginalServiceName(messageContext);
        ConfigurationContext context = messageContext.getConfigurationContext();
        // todo this logic has to change based on the logic we are storing data
        // into repository
        try {
            Credentials credentials = (Credentials) context.getProperty("credentials");
            Repository repo = (Repository) context.getProperty("repository");

            JCRRegistry jcr = new JCRRegistry(repo, credentials);

            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(
                    new StringReader(jcr.getWSDL(serviceName)));
            StAXOMBuilder builder = new StAXOMBuilder(reader);
            OMElement wsdlElement = builder.getDocumentElement();
            WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(wsdlElement.toString().getBytes());
            InputSource source = new InputSource(byteArrayInputStream);
            Definition wsdlDefinition = wsdlReader.readWSDL(null, source);

            // TODO based on the abstact wsdl content fill up the required
            // information using wsdl4j api
            SOAPFactory sf = OMAbstractFactory.getSOAP11Factory();
            SOAPEnvelope responseEnv = sf.createSOAPEnvelope();
            sf.createSOAPBody(responseEnv);
            responseEnv.getBody().addChild(wsdlElement);
            response = MessageContextBuilder.createOutMessageContext(messageContext);
            response.setEnvelope(responseEnv);
            response.getOperationContext().addMessageContext(response);
            AxisEngine.send(response);
        } catch (Exception fault) {
            log.error("Error creating response");
            throw fault;
        }
    }

    public void processgetAbstractWSDLOperation(MessageContext messageContext) throws Exception {
        MessageContext response = null;
        String serviceName = getOriginalServiceName(messageContext);
        ConfigurationContext context = messageContext.getConfigurationContext();
        // todo this logic has to change based on the logic we are storing data
        // into repository
        try {
            Credentials credentials = (Credentials) context.getProperty("credentials");
            Repository repo = (Repository) context.getProperty("repository");

            JCRRegistry jcr = new JCRRegistry(repo, credentials);

            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(
                    new StringReader(jcr.getWSDL(serviceName)));
            StAXOMBuilder builder = new StAXOMBuilder(reader);
            OMElement wsdlElement = builder.getDocumentElement();
            SOAPFactory sf = OMAbstractFactory.getSOAP11Factory();
            SOAPEnvelope responseEnv = sf.createSOAPEnvelope();
            sf.createSOAPBody(responseEnv);
            responseEnv.getBody().addChild(wsdlElement);
            response = MessageContextBuilder.createOutMessageContext(messageContext);
            response.setEnvelope(responseEnv);
            response.getOperationContext().addMessageContext(response);
            AxisEngine.send(response);
        } catch (Exception fault) {
            log.error("Error creating response");
            throw fault;
        }
    }

    private String getOriginalServiceName(MessageContext messageContext) {
        String toAddress = messageContext.getTo().getAddress();
        String[] values = Utils.parseRequestURLForServiceAndOperation(toAddress, messageContext
                .getConfigurationContext().getServiceContextPath());
        return values[0];
    }

}
