/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.egmi.management.scenario;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.management.MessagingService;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.problems.ProblemManager;
import ch.niceideas.eskimo.egmi.zookeeper.ZookeeperService;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractScenarioTest {

    protected MessagingService messagingService = null;

    @BeforeEach
    public void setUp() {
        messagingService = new MessagingService(5000);
    }

    /* scenario_BrickOffline */
    public void runScenario(String scenario) throws Exception {

        ManagementService ms = new ManagementService(false) {

            @Override
            public void saveRuntimeSetting(JsonWrapper settings) {
                // No Op
            }

            @Override
            public JsonWrapper loadRuntimeSettings() throws ManagementException {
                try {
                    return new JsonWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/" + scenario + "/runtime-config.json")));
                } catch (IOException e) {
                    throw new ManagementException (e);
                }
            }
        };
        ms.setTestConfig("192.168.10.71,192.168.10.72,192.168.10.73,192.168.10.74", "spark_eventlog,spark_data,flink_data,logstash_data,test");

        ms.setGlusterRemoteManager(new GlusterRemoteManager() {

            @Override
            public Map<Node, NodeStatus> getAllNodeStatus() throws GlusterRemoteException {
                try {
                    Map<Node, NodeStatus> retMap = new HashMap<>();
                    for (Node node : new Node[]{
                            Node.from("192.168.10.71"),
                            Node.from("192.168.10.72"),
                            Node.from("192.168.10.73"),
                            Node.from("192.168.10.74")}) {
                        InputStream nodeStatusIs = ResourceUtils.getResourceAsStream("ManagementServiceTest/" + scenario + "/" + node + ".json");
                        if (nodeStatusIs != null) {
                            retMap.put(node, new NodeStatus(StreamUtils.getAsString(nodeStatusIs)));
                        }
                    }
                    return retMap;
                } catch (IOException e) {
                    throw new GlusterRemoteException(e);
                }
            }
        });

        ms.setMessagingService(messagingService);

        ms.setProblemManager(new ProblemManager() {
            @Override
            public boolean resolutionIteration(SystemStatus newStatus) {
                // No-Op
                return false;
            }
        });

        ms.setZoopeeerService(new ZookeeperService(null, null, 0, null, true, "192.168.10.21,192.168.10.22,192.168.10.23") {

            @Override
            public boolean isMaster() {
                return true;
            }
        });

        ms.updateSystemStatus();

        SystemStatus ss = ms.getSystemStatus();
        assertNotNull (ss);

        String stringStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/" + scenario + "/resultStatus.json"));
        SystemStatus expectedStatus = new SystemStatus(stringStatus);

        expectedStatus.setValueForPath("hostname", InetAddress.getLocalHost().toString());

        //assertEquals (expectedStatus.getFormattedValue(), ss.getFormattedValue());
        assertTrue (expectedStatus.getJSONObject().similar(ss.getJSONObject()));
    }
}
