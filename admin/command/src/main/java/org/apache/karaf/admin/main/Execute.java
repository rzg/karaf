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
package org.apache.karaf.admin.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.basic.DefaultActionPreparator;
import org.apache.karaf.admin.command.*;
import org.apache.karaf.admin.command.ChangeSshPortCommand;
import org.apache.karaf.admin.internal.AdminServiceImpl;
import org.fusesource.jansi.AnsiConsole;

public class Execute {
    static Class<? extends Action> x = CreateCommand.class;
    private static final Class<?> [] COMMAND_CLASSES = new Class[] {
        CreateCommand.class,
        StartCommand.class,
        StopCommand.class,
        DestroyCommand.class,
        ListCommand.class,
        ChangeSshPortCommand.class,
        ChangeRmiRegistryPortCommand.class,
        ChangeRmiServerPortCommand.class};
    private static final Map<String, Class<?>> COMMANDS = new TreeMap<String, Class<?>>();
    static {
        for (Class<?> c : COMMAND_CLASSES) {
            Command ann = c.getAnnotation(Command.class);
            if (ann == null) {
                continue;
            }
            COMMANDS.put(ann.name(), c);
        }
    }    
    
    // For testing
    static boolean exitAllowed = true;

    public static void main(String[] args) throws Exception {
        AnsiConsole.systemInstall();

        if (args.length == 0) {
            listCommands();
            exit(0);
        }
        String commandName = args[0];
        Class<?> cls = COMMANDS.get(commandName);
        if (cls == null) {
            System.err.println("Command not found: " + commandName);
            exit(-1);
        }

        String storage = System.getProperty("karaf.instances");
        if (storage == null) {
            System.err.println("System property 'karaf.instances' is not set. \n" +
        		"This property needs to be set to the full path of the instance.properties file.");
            exit(-1);
        }
        File storageFile = new File(storage);
        System.setProperty("user.dir", storageFile.getParentFile().getParentFile().getCanonicalPath());
        
        Object command = cls.newInstance();
        if (command instanceof AdminCommandSupport) {
            try {
                execute((AdminCommandSupport) command, storageFile, args);
            } catch (Exception e) {
                System.err.println("Error execution command '" + commandName + "': " + e.getMessage());
                if (System.getProperty("karaf.showStackTrace") != null) {
                    throw e;
                }
            }
        } else {
            System.err.println("Not an admin command: " + commandName);
            exit(-1);
        }
    }
    
    static void execute(AdminCommandSupport command, File storageFile, String[] args) throws Exception {
        DefaultActionPreparator dap = new DefaultActionPreparator();
        List<Object> params = new ArrayList<Object>(Arrays.asList(args));
        params.remove(0); // this is the actual command name

        if (!dap.prepare(command, null, params)) {
            return;
        }
                
        AdminServiceImpl admin = new AdminServiceImpl();
        admin.setStorageLocation(storageFile);
        admin.init();
        command.setAdminService(admin);
        command.execute(null);
    }

    private static void listCommands() {
        System.out.println("Available commands:");
        for (Map.Entry<String, Class<?>> entry : COMMANDS.entrySet()) {
            Command ann = entry.getValue().getAnnotation(Command.class);
            System.out.printf("  %s - %s\n", entry.getKey(), ann.description());
        }
        
        System.out.println("Type 'command --help' for more help on the specified command.");
    }

    private static void exit(int rc) {
        if (exitAllowed) {
            System.exit(rc);
        } else {
            throw new RuntimeException("" + rc);
        }
    }
}
