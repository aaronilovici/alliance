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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.resource.Resource;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class JMSPostIngestPluginTest {

  private JMSPostIngestPlugin postIngestPlugin = null;
  private CatalogFramework catalogFramework = null;
  private CreateResponse createResponse = null;
  private CreateRequest createRequest = null;
  private MetacardImpl metacard = null;

  private Map<String, Serializable> requestProperties;

  @Before
  public void setUp() throws Exception {
    this.catalogFramework = mock(CatalogFramework.class);
    this.postIngestPlugin = new JMSPostIngestPlugin();
    this.postIngestPlugin.setCatalogFramework(catalogFramework);

    this.createResponse = mock(CreateResponse.class);
    this.createRequest = mock(CreateRequest.class);

    this.requestProperties = new HashMap<>();
    this.metacard = new MetacardImpl();
    metacard.setId("123456");
    metacard.setResourceSize("1048576");
    when(createResponse.getCreatedMetacards()).thenReturn(Collections.singletonList(metacard));
    when(createResponse.getRequest()).thenReturn(createRequest);
    when(createRequest.getProperties()).thenReturn(requestProperties);
  }

  @Test
  public void testProcessCreateResponse() throws Exception {
    ActiveMQConnectionFactory connectionFactory = mock(ActiveMQConnectionFactory.class);
    postIngestPlugin.setConnectionFactory(connectionFactory);

    Connection connection = mock(Connection.class);
    postIngestPlugin.setConnection(connection);

    Session session = mock(Session.class);
    Queue destination = mock(Queue.class);
    MessageProducer producer = mock(MessageProducer.class);
    ResourceResponse resourceResponse = mock(ResourceResponse.class);
    Resource resource = mock(Resource.class);

    when(connectionFactory.createConnection()).thenReturn(connection);
    when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);
    doNothing().when(connection).start();
    when(session.createQueue(any())).thenReturn(destination);
    when(session.createProducer(destination)).thenReturn(producer);
    when(catalogFramework.getResource(any(), any())).thenReturn(resourceResponse);
    when(resourceResponse.getResource()).thenReturn(resource);

    postIngestPlugin.process(createResponse);
    verify(producer, times(1)).send(any());
  }
}
