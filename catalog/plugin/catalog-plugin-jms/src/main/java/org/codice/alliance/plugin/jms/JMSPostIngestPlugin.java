/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.alliance.plugin.jms;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.ResourceRequestByProductUri;
import ddf.catalog.plugin.PluginExecutionException;
import ddf.catalog.plugin.PostIngestPlugin;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This post-ingest plugin sends data to a JMS queue */
public class JMSPostIngestPlugin implements PostIngestPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(JMSPostIngestPlugin.class);
  private static final String BROKER_URL = "vm://localhost";
  private static final String QUEUE_NAME = System.getProperty("activemq.queue.name");

  private CatalogFramework catalogFramework;
  private ActiveMQConnectionFactory connectionFactory;
  private Connection connection;

  @Override
  public CreateResponse process(CreateResponse createResponse) throws PluginExecutionException {
    if (createResponse == null) {
      throw new PluginExecutionException("process(): argument 'createResponse' may not be null.");
    }
    try {
      sendJMSMessage(
          new HashSet<>(createResponse.getCreatedMetacards()),
          createResponse.getRequest().getProperties());
    } catch (InterruptedException e) {
      LOGGER.debug("Interrupt received while processing.", e);
      Thread.currentThread().interrupt();
    }
    return createResponse;
  }

  @Override
  public UpdateResponse process(UpdateResponse updateResponse) throws PluginExecutionException {
    return updateResponse;
  }

  @Override
  public DeleteResponse process(DeleteResponse deleteResponse) throws PluginExecutionException {
    return deleteResponse;
  }

  private void sendJMSMessage(Set<Metacard> metacards, Map<String, Serializable> properties)
      throws InterruptedException {

    try {
      // Create connection
      if (connectionFactory == null) {
        LOGGER.debug("Creating connection to ActiveMQ broker " + BROKER_URL);
        connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
      }
      connection = connectionFactory.createConnection();
      connection.start();

      // Create session
      LOGGER.debug(String.format("Creating session to ActiveMQ queue %s", QUEUE_NAME));
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Destination destination = session.createQueue(QUEUE_NAME);

      // Create message producer
      MessageProducer producer = session.createProducer(destination);
      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      // Iterate through all metacards in CreateResponse
      for (Metacard metacard : metacards) {
        // Obtain resource URI
        URI resourceURI = metacard.getResourceURI();
        String sourceName = metacard.getSourceId();
        final ResourceRequest resourceRequest = new ResourceRequestByProductUri(resourceURI);

        // Obtain resource itself
        Resource resource = null;
        ResourceResponse resourceResponse = null;
        try {
          resourceResponse = catalogFramework.getResource(resourceRequest, sourceName);
        } catch (IOException | ResourceNotFoundException | ResourceNotSupportedException e) {
          LOGGER.error("Unable to retrieve metacard resource", e);
        }

        if (resourceResponse != null) {
          resource = resourceResponse.getResource();
        }

        // Send message to queue with serializable resource
        if (resource != null) {
          LOGGER.debug(
              String.format(
                  "Sending resource to ActiveMQ queue %s: %s", QUEUE_NAME, resource.getName()));
          JMSResourceWrapper wrappedResource = new JMSResourceWrapper(resource);
          ObjectMessage message = session.createObjectMessage(wrappedResource);
          producer.send(message);
        }
      }
    } catch (JMSException e) {
      LOGGER.error("Unable to establish connection to JMS Queue", e);
    }
  }

  public void setCatalogFramework(CatalogFramework catalogFramework) {
    this.catalogFramework = catalogFramework;
  }

  public void setConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public void setConnection(Connection connection) {
    this.connection = connection;
  }
}
