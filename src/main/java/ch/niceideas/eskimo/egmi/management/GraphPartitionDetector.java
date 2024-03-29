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

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.NodeStatusException;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.problems.NodePartitioned;
import ch.niceideas.eskimo.egmi.problems.ProblemManager;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class GraphPartitionDetector {

    private static final Logger logger = Logger.getLogger(GraphPartitionDetector.class);

    public static void detectGraphPartitioning(
            ProblemManager problemManager,
            Set<Node> allNodes,
            SystemStatus newStatus,
            Map<Node, NodeStatus> nodesStatus) throws NodeStatusException  {

        // 0. Build data structure
        Map<Node, GraphNode> nodes = buildNodeGraph(allNodes, nodesStatus);

        // Find total number of volumes
        int totalVolumesCounter = nodesStatus.values().stream()
                .map(ns -> {
                    try {
                        return ns.getAllVolumes().size();
                    } catch (NodeStatusException e) {
                        logger.debug (e, e);
                        return 0;
                    }
                })
                .max(Comparator.naturalOrder())
                .orElse(1);
        if (totalVolumesCounter == 0) { // hack for when there are no volumes whatsoever
            totalVolumesCounter = 1;
        }

        // 1. For every node, count number of nodes reachable on the connection graph from the node
        Map<Node, Integer> counters = buildPeerTimesVolumeCounters(allNodes, nodes, nodesStatus);

        // 2. If every node, has same count as total number of nodes, there is no partitioning, we're good
        boolean allGood = true;
        for (Node host : allNodes) {
            int counter = counters.get(host);
            if (counter != allNodes.size() * totalVolumesCounter) {
                allGood = false;
                break;
            }
        }

        if (!allGood) {

            // 3. If some node have different counts, flag the all nodes having a lesser count as disconnected

            // find highest and smallest count
            int smallest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
            for (Node host : allNodes) {
                int counter = counters.get(host);
                smallest = Math.min (counter, smallest);
                highest = Math.max (counter, highest);
            }

            // if highest == smallest, then flag all nodes as PARTITIONED
            if (highest == smallest) {
                for (Node host : allNodes) {
                    flagNodePartitioned(problemManager, newStatus, host);
                }
            }

            // otherwise flag all those different than highest as PARTITIONED
            else {
                for (Node host : allNodes) {
                    int counter = counters.get(host);
                    if (counter != highest) {
                        flagNodePartitioned(problemManager, newStatus, host);
                    }
                }
            }
        }
    }

    public static Map<Node, Integer> buildPeerTimesVolumeCounters(Set<Node> allNodes, Map<Node, GraphNode> nodes, Map<Node, NodeStatus> nodesStatus) {

        // if at least one node has volumes configured, then I need to account volumes as well
        boolean alsoAccountVolumes = nodesStatus.values().stream()
                .anyMatch (nodeStatus -> {
                    try {
                        return nodeStatus.getAllVolumes().size() > 0;
                    } catch (NodeStatusException e) {
                        return false;
                    }
                });

        Map<Node, Integer> counters = new HashMap<>();
        for (Node host : allNodes) {
            Set<Node> visited = buildPeerNetwork(nodes, host);

            int peerCounter = visited.size();
            int targetCounter = peerCounter;

            if (alsoAccountVolumes) {
                int nodeVolumesCounter = 0;
                NodeStatus nodeStatus = nodesStatus.get(host);
                if (nodeStatus != null) {
                    try {
                        nodeVolumesCounter = nodeStatus.getAllVolumes().size();
                    } catch (NodeStatusException ignored) {
                        // ignored here
                    }
                }

                targetCounter = nodeVolumesCounter * peerCounter;
            }

            counters.put(host, targetCounter);
        }
        return counters;
    }

    public static Set<Node> buildPeerNetwork(Map<Node, GraphNode> nodes, Node host) {
        Stack<GraphNode> stack = new Stack<>();
        GraphNode current = nodes.get(host);
        stack.push (current);

        Set<GraphNode> visited = new HashSet<>();
        visited.add(current);

        while (!stack.isEmpty()) {
            current = stack.pop();
            for (GraphNode peer : current.getPeers()) {
                if (!visited.contains(peer)) {
                    stack.push (peer);
                    visited.add(peer);
                }
            }
        }
        return visited.stream()
                .map(GraphNode::getHost)
                .collect(Collectors.toSet());
    }

    public static Map<Node, GraphNode> buildNodeGraph(Set<Node> allNodes, Map<Node, NodeStatus> nodesStatus) throws NodeStatusException {
        Map<Node, GraphNode> nodes = new HashMap<>();
        for (Node host : allNodes) {
            GraphNode hostNode = nodes.computeIfAbsent(host, (key) -> new GraphNode(host));
            NodeStatus status = nodesStatus.get(host);
            if (status != null) {
                for (Node peer : status.getAllPeers()) {
                    if (!peer.matches("localhost")) {
                        GraphNode peerNode = nodes.computeIfAbsent(peer, (key) -> new GraphNode(peer));
                        hostNode.addPeer(peerNode);
                    }
                }
            }
        }
        return nodes;
    }

    private static void flagNodePartitioned(ProblemManager problemManager, SystemStatus newStatus, Node host) {
        String prevStatus =  newStatus.getNodeStatus(host);
        if (StringUtils.isBlank(prevStatus) || !prevStatus.equals("KO")) { // don't overwrite KO node
            newStatus.overrideNodeStatus (host, "PARTITIONED");
            problemManager.addProblem(new NodePartitioned(new Date(), host));
        }
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode
    public static class GraphNode {

        @Getter
        private final Node host;

        @EqualsAndHashCode.Exclude
        private final Map<Node, GraphNode> peers = new HashMap<>();

        public void addPeer (GraphNode node) {
            peers.put (node.getHost(), node);
        }

        public Collection<GraphNode> getPeers () {
            return peers.values();
        }

    }
}
