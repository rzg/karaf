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
package org.apache.karaf.shell.config;

import java.io.File;
import java.util.Dictionary;
import java.util.Enumeration;

import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.felix.utils.properties.Properties;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

@Command(scope = "config", name = "update", description = "Saves and propagates changes from the configuration being edited.")
public class UpdateCommand extends ConfigCommandSupport {

    private final String FELIX_FILEINSTALL_FILENAME = "felix.fileinstall.filename";

    @Option(name = "-b", aliases = { "--bypass-storage" }, multiValued = false, required = false, description = "Do not store the configuration in a properties file, but feed it directly to ConfigAdmin")
    private boolean bypassStorage;

    private File storage;

    public File getStorage() {
        return storage;
    }

    public void setStorage(File storage) {
        this.storage = storage;
    }

    protected void doExecute(ConfigurationAdmin admin) throws Exception {
        Dictionary props = getEditedProps();
        if (props == null) {
            System.err.println("No configuration is being edited--run the edit command first");
            return;
        }

        String pid = (String) this.session.get(PROPERTY_CONFIG_PID);
        if (!bypassStorage && storage != null) {
            File storageFile = new File(storage, pid + ".cfg");
            Configuration cfg = admin.getConfiguration(pid, null);
            if (cfg != null && cfg.getProperties() != null) {
                Object val = cfg.getProperties().get(FELIX_FILEINSTALL_FILENAME);
                if (val instanceof String) {
                    if (((String) val).startsWith("file:")) {
                        val = ((String) val).substring("file:".length());
                    }
                    storageFile = new File((String) val);
                }
            }
            Properties p = new Properties(storageFile);
            for (Enumeration keys = props.keys(); keys.hasMoreElements();) {
                Object key = keys.nextElement();
                if (!Constants.SERVICE_PID.equals(key)
                        && !ConfigurationAdmin.SERVICE_FACTORYPID.equals(key)
                        && !FELIX_FILEINSTALL_FILENAME.equals(key)) {
                    p.put((String) key, (String) props.get(key));
                }
            }
            storage.mkdirs();
            p.save();
        } else {
            Configuration cfg = admin.getConfiguration(pid, null);
            if (cfg.getProperties() == null) {
                String[] pids = parsePid(pid);
                if (pids[1] != null) {
                    cfg = admin.createFactoryConfiguration(pids[0], null);
                }
            }
            if (cfg.getBundleLocation() != null) {
                cfg.setBundleLocation(null);
            }
            cfg.update(props);
        }
        this.session.put(PROPERTY_CONFIG_PID, null);
        this.session.put(PROPERTY_CONFIG_PROPS, null);
    }

    private String[] parsePid(String pid) {
        int n = pid.indexOf('-');
        if (n > 0) {
            String factoryPid = pid.substring(n + 1);
            pid = pid.substring(0, n);
            return new String[] { pid, factoryPid };
        } else {
            return new String[] { pid, null };
        }
    }


}
