/* 
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

package org.apache.karaf.shell.http;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.ops4j.pax.web.service.spi.ServletEvent;
import org.ops4j.pax.web.service.spi.ServletListener;

public class ServletEventHandler implements ServletListener {

	Map<String, ServletEvent> servletEvents =  new HashMap<String, ServletEvent>();
	
	public void servletEvent(ServletEvent event) {
		servletEvents.put(event.getServletName(), event);
	}

	/**
	 * @return the servletEvents
	 */
	public Collection<ServletEvent> getServletEvents() {
		return servletEvents.values();
	}

}
