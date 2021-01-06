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

import ddf.catalog.data.Metacard;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.plugin.PluginExecutionException;
import ddf.catalog.plugin.PostIngestPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This post-ingest plugin sends data to a JMS queue
 */
public class JMSPostIngestPlugin implements PostIngestPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(JMSPostIngestPlugin.class);

  static QueueConnection connection;
  static QueueReceiver queueReceiver;
  static Queue queue;

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
      LOGGER.debug("Interrupt received while doing image processing.", e);
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
      InitialContext ctx = new InitialContext();
      QueueConnectionFactory factory = (QueueConnectionFactory)ctx.lookup("myQueueConnectionFactory");
      QueueConnection conn = factory.createQueueConnection();
      conn.start();

      QueueSession session = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = (Queue)ctx.lookup("myQueue");
      QueueSender sender = session.createSender(queue);
      TextMessage msg = session.createTextMessage();

      msg.setText();
      sender.send(msg);
      conn.close();
    } catch (NamingException | JMSException e) {
      LOGGER.error("Unable to send message to JMS Queue", e);
    }
  }
}
