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
package org.apache.karaf.shell.obr;

import java.util.List;

import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

@Command(scope = "obr", name = "deploy", description = "Deploys a list of bundles using OBR service.")
public class DeployCommand extends ObrCommandSupport {

    @Argument(index = 0, name = "bundles", description = "List of bundle names to deploy (separated by whitespaces)", required = true, multiValued = true)
    protected List<String> bundles;

    @Option(name = "-s", aliases = { "--start" }, description = "Start the deployed bundles", required = false, multiValued = false)
    protected boolean start = false;

    protected void doExecute(RepositoryAdmin admin) throws Exception {
        doDeploy(admin, bundles, start);
    }

}
