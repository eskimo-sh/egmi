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

package ch.niceideas.eskimo.egmi.management;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.model.Volume;
import ch.niceideas.eskimo.egmi.problems.Problem;
import ch.niceideas.eskimo.egmi.problems.ProblemManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ManagementServiceTest {

    private ManagementService ms = null;

    private NodeStatus node1 = null;
    private NodeStatus node2 = null;
    private NodeStatus node3 = null;
    private NodeStatus node4 = null;

    @BeforeEach
    public void setUp() throws Exception{

        ms = new ManagementService();
        ms.setProblemManager(new ProblemManager() {
            @Override
            public void addProblem(Problem problem) {
                // No Op
            }
        });

        node1 = new NodeStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/options/192.168.56.21.json")));
        node2 = new NodeStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/options/192.168.56.22.json")));
        node3 = new NodeStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/options/192.168.56.23.json")));
        node4 = new NodeStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/options/192.168.56.24.json")));
    }

    @Test
    public void testComputeNumberBricks_LOG_DISPATCH() {

        ms.targetNumberBricksString = "LOG_DISPATCH";
        ms.defaultNumberReplica = 3;

        // Testing 1 node
        ms.setTestConfig(String.join (",", "a ".repeat(1).trim().split (" ")), null);
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(2).trim().split (" ")), null);
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(3).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(4).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(5).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(6).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(10).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(20).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(50).trim().split (" ")), null);
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(100).trim().split (" ")), null);
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());
    }

    @Test
    public void testComputeNumberBricks_ALL() {

        ms.targetNumberBricksString = "ALL";
        ms.defaultNumberReplica = 3;

        // Testing 1 node
        ms.setTestConfig(String.join (",", "a ".repeat(1).trim().split (" ")), null);
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(2).trim().split (" ")), null);
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(3).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(4).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(5).trim().split (" ")), null);
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(6).trim().split (" ")), null);
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(10).trim().split (" ")), null);
        assertEquals (9, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(20).trim().split (" ")), null);
        assertEquals (18, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(50).trim().split (" ")), null);
        assertEquals (48, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.setTestConfig(String.join (",", "a ".repeat(100).trim().split (" ")), null);
        assertEquals (99, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());
    }

    @Test
    public void testComputeNumberBricks_FIXED() {

        ms.defaultNumberReplica = 5;

        // Testing 1 node
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(1).trim().split (" ")), null);
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(2).trim().split (" ")), null);
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(3).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(4).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(5).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.targetNumberBricksString = "3";
        ms.setTestConfig(String.join (",", "a ".repeat(6).trim().split (" ")), null);
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.targetNumberBricksString = "10";
        ms.setTestConfig(String.join (",", "a ".repeat(10).trim().split (" ")), null);
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.targetNumberBricksString = "10";
        ms.setTestConfig(String.join (",", "a ".repeat(20).trim().split (" ")), null);
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.targetNumberBricksString = "10";
        ms.setTestConfig(String.join (",", "a ".repeat(50).trim().split (" ")), null);
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.targetNumberBricksString = "10";
        ms.setTestConfig(String.join (",", "a ".repeat(100).trim().split (" ")), null);
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());
    }

    @Test
    public void testGetRuntimeNodes() throws Exception {

        ms.setTestConfig("192.168.56.20", null);

        File tmp = File.createTempFile("test", "egmi-mgmt-service");
        assertTrue (tmp.delete());
        assertTrue (tmp.mkdir());

        ms.setTestConfigStoragePath (tmp.getAbsolutePath());

        Map<Node, NodeStatus> nodesStatus = new HashMap<>(){{
            put (Node.from ("192.169.56.21"), node1);
            put (Node.from ("192.169.56.22"), node2);
            put (Node.from ("192.169.56.23"), node3);
            put (Node.from ("192.169.56.24"), node4);
        }};

        Set<Node> allNodes = ms.getRuntimeNodes(nodesStatus);

        assertNotNull(allNodes);
        assertEquals(5, allNodes.size());

        List<Node> sortedNodes = new ArrayList<>(allNodes);
        sortedNodes.sort(Comparator.comparing(Node::getAddress));

        assertEquals("192.168.56.20", sortedNodes.get(0).getAddress());
        assertEquals("192.168.56.21", sortedNodes.get(1).getAddress());
        assertEquals("192.168.56.22", sortedNodes.get(2).getAddress());
        assertEquals("192.168.56.23", sortedNodes.get(3).getAddress());
        assertEquals("192.168.56.24", sortedNodes.get(4).getAddress());

        FileUtils.delete(tmp);
    }

    @Test
    public void testGetRuntimeVolumes() throws Exception {

        ms.setTestConfig("192.168.56.20", "test1,test2");

        File tmp = File.createTempFile("test", "egmi-mgmt-service");
        assertTrue (tmp.delete());
        assertTrue (tmp.mkdir());

        ms.setTestConfigStoragePath (tmp.getAbsolutePath());

        Map<Node, NodeStatus> nodesStatus = new HashMap<>(){{
            put (Node.from ("192.168.56.21"), node1);
            put (Node.from ("192.168.56.22"), node2);
            put (Node.from ("192.168.56.23"), node3);
            put (Node.from ("192.168.56.24"), node4);
        }};

        Set<Volume> allVolumes = ms.getRuntimeVolumes(nodesStatus);

        assertNotNull(allVolumes);
        assertEquals(10, allVolumes.size());

        List<Volume> sortedVolumes = new ArrayList<>(allVolumes);
        sortedVolumes.sort(Comparator.naturalOrder());

        assertEquals(Volume.from("flink_completed_jobs"), sortedVolumes.get(0));
        assertEquals(Volume.from("flink_data"), sortedVolumes.get(1));
        assertEquals(Volume.from("kafka_data"), sortedVolumes.get(2));
        assertEquals(Volume.from("kubernetes_registry"), sortedVolumes.get(3));
        assertEquals(Volume.from("kubernetes_shared"), sortedVolumes.get(4));
        assertEquals(Volume.from("logstash_data"), sortedVolumes.get(5));
        assertEquals(Volume.from("spark_data"), sortedVolumes.get(6));
        assertEquals(Volume.from("spark_eventlog"), sortedVolumes.get(7));
        assertEquals(Volume.from("test1"), sortedVolumes.get(8));
        assertEquals(Volume.from("test2"), sortedVolumes.get(9));

        FileUtils.delete(tmp);
    }

    @Test
    public void testBuildNodeInfo() throws Exception {

        ms.setTestConfig("192.168.56.20", null);

        File tmp = File.createTempFile("test", "egmi-mgmt-service");
        assertTrue (tmp.delete());
        assertTrue (tmp.mkdir());

        ms.setTestConfigStoragePath (tmp.getAbsolutePath());

        Map<Node, NodeStatus> nodesStatus = new HashMap<>(){{
            put (Node.from ("192.168.56.21"), node1);
            put (Node.from ("192.168.56.22"), node2);
            put (Node.from ("192.168.56.23"), node3);
            put (Node.from ("192.168.56.24"), node4);
        }};

        Set<Node> allNodes = ms.getRuntimeNodes(nodesStatus);

        List<JSONObject> nodesInfo = ms.buildNodeInfo(nodesStatus, allNodes);

        assertNotNull(nodesInfo);
        assertEquals(5, nodesInfo.size());

        assertEquals("{\"host\":\"192.168.56.20\",\"status\":\"KO\"}", nodesInfo.get(0).toString());
        assertEquals("{\"host\":\"192.168.56.21\",\"volumes\":\"flink_completed_jobs, flink_data, kafka_data, kubernetes_registry, kubernetes_shared, logstash_data, spark_data, spark_eventlog\",\"nbr_bricks\":8,\"status\":\"OK\"}", nodesInfo.get(1).toString());
        assertEquals("{\"host\":\"192.168.56.22\",\"volumes\":\"flink_completed_jobs, flink_data, kubernetes_registry, kubernetes_shared, logstash_data, spark_data\",\"nbr_bricks\":6,\"status\":\"OK\"}", nodesInfo.get(2).toString());
        assertEquals("{\"host\":\"192.168.56.23\",\"volumes\":\"flink_completed_jobs, flink_data, kafka_data, spark_data, spark_eventlog\",\"nbr_bricks\":5,\"status\":\"OK\"}", nodesInfo.get(3).toString());
        assertEquals("{\"host\":\"192.168.56.24\",\"volumes\":\"kafka_data, kubernetes_registry, kubernetes_shared, logstash_data, spark_eventlog\",\"nbr_bricks\":5,\"status\":\"OK\"}", nodesInfo.get(4).toString());

        FileUtils.delete(tmp);
    }

    @Test
    public void testGetSystemStatus() throws Exception {

        ms.setTestConfig("192.168.56.20", "test1,test2");

        File tmp = File.createTempFile("test", "egmi-mgmt-service");
        assertTrue (tmp.delete());
        assertTrue (tmp.mkdir());

        ms.setTestConfigStoragePath (tmp.getAbsolutePath());

        Map<Node, NodeStatus> nodesStatus = new HashMap<>(){{
            put (Node.from ("192.168.56.21"), node1);
            put (Node.from ("192.168.56.22"), node2);
            put (Node.from ("192.168.56.23"), node3);
            put (Node.from ("192.168.56.24"), node4);
        }};

        Set<Node> allNodes = ms.getRuntimeNodes(nodesStatus);

        Set<Volume> allVolumes = ms.getRuntimeVolumes(nodesStatus);

        SystemStatus ss = ms.getSystemStatus("testhost", nodesStatus, allNodes, allVolumes);

        //System.err.println (ss.getFormattedValue());

        SystemStatus expected = new SystemStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/options/resultSystemStatus.json")));

        //assertEquals (expected.getFormattedValue(), ss.getFormattedValue());
        assertTrue (expected.getJSONObject().similar(ss.getJSONObject()));
    }

}
