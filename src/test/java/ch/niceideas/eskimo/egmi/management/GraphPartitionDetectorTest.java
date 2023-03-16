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

import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.problems.ProblemManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GraphPartitionDetectorTest {

    private final Node node1 = Node.from("192.168.56.21");
    private final Node node2 = Node.from("192.168.56.22");
    private final Node node3 = Node.from("192.168.56.23");
    private final Node node4 = Node.from("192.168.56.24");
    private final Node node5 = Node.from("192.168.56.25");

    @Test
    public void testDetectGraphPartitioning() throws Exception {

        ProblemManager problemManager = new ProblemManager();

        SystemStatus systemStatus = new SystemStatus("{nodes : [" +
                "    { host: '192.168.56.21'}," +
                "    { host: '192.168.56.22'}," +
                "    { host: '192.168.56.23'}," +
                "    { host: '192.168.56.24'}," +
                "    { host: '192.168.56.25'}," +
                "]}");

        Map<Node, NodeStatus> nodesStatus = getNodesStatus (false);

        GraphPartitionDetector.detectGraphPartitioning(problemManager, nodesStatus.keySet(), systemStatus, nodesStatus);

        assertNotNull(problemManager.getProblemSummary());
        assertEquals ("2 Node Partitioned", problemManager.getProblemSummary());
    }

    @Test
    public void tesBuildPeerTimesVolumeCounters() throws Exception {

        Map<Node, NodeStatus> nodesStatus = getNodesStatus (false);

        Map<Node, GraphPartitionDetector.GraphNode> nodeGraph =  GraphPartitionDetector.buildNodeGraph(nodesStatus.keySet(), nodesStatus);

        Map<Node, Integer> result = GraphPartitionDetector.buildPeerTimesVolumeCounters(
                nodesStatus.keySet(), nodeGraph, nodesStatus);

        assertNotNull (result);
        assertEquals (5, result.size());

        assertEquals (6, result.get(node1));
        assertEquals (6, result.get(node2));
        assertEquals (6, result.get(node3));
        assertEquals (4, result.get(node4));
        assertEquals (4, result.get(node5));
    }

    @Test
    public void testBuildPeerNetwork() throws Exception{

        Map<Node, NodeStatus> nodesStatus = getNodesStatus (true);

        Map<Node, GraphPartitionDetector.GraphNode> nodeGraph =  GraphPartitionDetector.buildNodeGraph(nodesStatus.keySet(), nodesStatus);

        assertEquals("192.168.56.21,192.168.56.22,192.168.56.23",
                GraphPartitionDetector.buildPeerNetwork(nodeGraph, node1).stream().map(Node::getAddress).collect(Collectors.joining(",")));
        assertEquals("192.168.56.21,192.168.56.22,192.168.56.23",
                GraphPartitionDetector.buildPeerNetwork(nodeGraph, node2).stream().map(Node::getAddress).collect(Collectors.joining(",")));
        assertEquals("192.168.56.21,192.168.56.22,192.168.56.23",
                GraphPartitionDetector.buildPeerNetwork(nodeGraph, node3).stream().map(Node::getAddress).collect(Collectors.joining(",")));

        assertEquals("192.168.56.24,192.168.56.25",
                GraphPartitionDetector.buildPeerNetwork(nodeGraph, node4).stream().map(Node::getAddress).collect(Collectors.joining(",")));
        assertEquals("192.168.56.24,192.168.56.25",
                GraphPartitionDetector.buildPeerNetwork(nodeGraph, node5).stream().map(Node::getAddress).collect(Collectors.joining(",")));
    }

    @Test
    public void testbuildNodeGraph() throws Exception {

        Map<Node, NodeStatus> nodesStatus = getNodesStatus (false);

        Map<Node, GraphPartitionDetector.GraphNode> nodeGraph =  GraphPartitionDetector.buildNodeGraph(nodesStatus.keySet(), nodesStatus);

        assertNotNull(nodeGraph);
        assertEquals(5, nodeGraph.size());

        assertEquals("192.168.56.22,192.168.56.23",
                nodeGraph.get(node1).getPeers().stream().map(gNode -> gNode.getHost().getAddress()).collect(Collectors.joining(",")));
        assertEquals("192.168.56.21,192.168.56.23",
                nodeGraph.get(node2).getPeers().stream().map(gNode -> gNode.getHost().getAddress()).collect(Collectors.joining(",")));
        assertEquals("192.168.56.21,192.168.56.22",
                nodeGraph.get(node3).getPeers().stream().map(gNode -> gNode.getHost().getAddress()).collect(Collectors.joining(",")));

        assertEquals("192.168.56.25",
                nodeGraph.get(node4).getPeers().stream().map(gNode -> gNode.getHost().getAddress()).collect(Collectors.joining(",")));
        assertEquals("192.168.56.24",
                nodeGraph.get(node5).getPeers().stream().map(gNode -> gNode.getHost().getAddress()).collect(Collectors.joining(",")));
    }

    /* Set partial to true to have only partially connected network */
    private Map<Node, NodeStatus> getNodesStatus(boolean partial) {
        return new HashMap<>(){{
            put(node1, new NodeStatus("{" +
                    "    peers: [" +
                    (!partial ?
                        "        {uid: 'a', hostname: '192.168.56.22', state: 'OK'},"
                        : "" ) +
                    "        {uid: 'a', hostname: '192.168.56.23', state: 'OK'}" +
                    "    ]," +
                    "    volumes: [" +
                    "       {name: 'a'},  {name: 'b'}"+
                    "    ]" +
                    "}"));
            put(node2, new NodeStatus("{" +
                    "    peers: [" +
                    (!partial ?
                            "        {uid: 'a', hostname: '192.168.56.21', state: 'OK'},"
                            : "") +
                    "        {uid: 'a', hostname: '192.168.56.23', state: 'OK'}" +
                    "    ]," +
                    "    volumes: [" +
                    "       {name: 'a'},  {name: 'b'}"+
                    "    ]" +
                    "}"));
            put(node3, new NodeStatus("{" +
                    "    peers: [" +
                    "        {uid: 'a', hostname: '192.168.56.21', state: 'OK'}," +
                    "        {uid: 'a', hostname: '192.168.56.22', state: 'OK'}" +
                    "    ]," +
                    "    volumes: [" +
                    "       {name: 'a'},  {name: 'b'}"+
                    "    ]" +
                    "}"));

            put(node4, new NodeStatus("{" +
                    "    peers: [" +
                    "        {uid: 'a', hostname: '192.168.56.25', state: 'OK'}" +
                    "    ]," +
                    "    volumes: [" +
                    "       {name: 'c'},  {name: 'd'}"+
                    "    ]" +
                    "}"));
            put(node5, new NodeStatus("{" +
                    "    peers: [" +
                    "        {uid: 'a', hostname: '192.168.56.24', state: 'OK'}" +
                    "    ]," +
                    "    volumes: [" +
                    "       {name: 'c'},  {name: 'd'}"+
                    "    ]" +
                    "}"));
        }};
    }
}
