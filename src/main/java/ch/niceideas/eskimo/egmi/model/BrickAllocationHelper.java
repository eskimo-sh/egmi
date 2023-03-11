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

package ch.niceideas.eskimo.egmi.model;

import ch.niceideas.eskimo.egmi.problems.CommandContext;
import ch.niceideas.eskimo.egmi.problems.ResolutionStopException;

import java.util.*;
import java.util.stream.Collectors;

public class BrickAllocationHelper {

    public static List<BrickId> buildReplicaUnallocation (Map<BrickId, BrickInformation> brickInformations, Node vanishedNode, int currentNbReplicas, int currentNbShards ) {

        // Sort BrickIds to have replicas collocated, removing
        List<BrickId> sortedBrickIds = new ArrayList<>(brickInformations.keySet());
        sortedBrickIds.sort(new BrickIdNumberComparator(brickInformations));

        if (sortedBrickIds.size() == 0) {
            return Collections.emptyList();
        }

        List<BrickId> retBrickIds = new ArrayList<>();

        if (currentNbShards == 1 || currentNbReplicas == 1) {

            // in this case it's easy whatever happens I can remove only one brick

            // find vanished node brick
            for (BrickId brickId : sortedBrickIds) {
                if (brickId.getNode().equals(vanishedNode)) {
                    retBrickIds.add(brickId);
                }
            }

        } else {

            // need to find the replica number for vanished node
            int replicaNumber = -1;
            for (BrickId brickId : sortedBrickIds) {
                if (brickId.getNode().equals(vanishedNode)) {
                    BrickInformation brickInfo = brickInformations.get(brickId);
                    replicaNumber = brickInfo.getNumber() % currentNbReplicas;
                }
            }

            // need to remove the corresponding replica number for every shard
            for (BrickId brickId : sortedBrickIds) {
                BrickInformation brickInfo = brickInformations.get(brickId);
                if (brickInfo.getNumber() % currentNbReplicas == replicaNumber) {
                    retBrickIds.add(brickId);
                }
            }
        }

        return retBrickIds;
    }

    public static List<BrickId> buildNewShardBrickAllocation(
            Volume volume, Map<BrickId, BrickInformation> brickInformations, int currentNbReplicas, String volumePath, List<Node> sortedNodes) {

        // find free nodes
        List<Node> freeNodes = sortedNodes.stream()
                .filter(node -> !brickInformations.keySet().stream().map(BrickId::getNode).collect(Collectors.toSet()).contains(node))
                .collect(Collectors.toList());

        // Build brick allocation
        List<BrickId> brickIds = new ArrayList<>();
        for (int i = 0; i < currentNbReplicas; i++) {

            Node brickNode = freeNodes.stream().findFirst().orElseThrow(IllegalStateException::new);
            freeNodes.remove (brickNode);
            String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume;
            brickIds.add (BrickId.fromNodeAndPath (brickNode, path));
        }
        return brickIds;
    }

    public static List<BrickId> buildNewReplicasBrickAllocation(
            Volume volume, Map<BrickId, BrickInformation> brickInformations, int currentNbReplicas, int currentNbShards, String volumePath, List<Node> sortedNodes) throws ResolutionStopException {

        // 1. Map for every shards the nodes running them
        List<BrickId> sortedBrickIds = new ArrayList<>(brickInformations.keySet());
        sortedBrickIds.sort(new BrickIdNumberComparator(brickInformations));
        Map<Integer, Set<Node>> brickNodesMap = new HashMap<>();
        int brickCounter = 0;
        for (BrickId brickId : sortedBrickIds) {

            int shardNumber =  (brickCounter / currentNbReplicas);
            brickNodesMap.computeIfAbsent(shardNumber, nbr -> new HashSet<>()).add(brickId.getNode());

            brickCounter++;
        }

        // 2. Then for every shards, find ideally a free node or else a node not running it
        Map<Integer, Node> newReplicaNodes = new HashMap<>();
        for (int shardNbr : brickNodesMap.keySet()) {

            Node targetNode;

            Node freeNode = sortedNodes.stream()
                    .filter(node -> !brickInformations.keySet().stream().map(BrickId::getNode).collect(Collectors.toSet()).contains(node))
                    .filter(node -> !newReplicaNodes.containsValue(node))
                    .findFirst().orElse(null);
            if (freeNode != null) {
                targetNode = freeNode;
            } else {
                targetNode = sortedNodes.stream()
                        .filter(node -> !brickNodesMap.get(shardNbr).contains(node))
                        .filter(node -> !newReplicaNodes.containsValue(node))
                        .findFirst().orElseThrow(() -> new ResolutionStopException("Impossible to find a node not running shard " + shardNbr + " replicas"));
                if (targetNode == null) {
                    throw new ResolutionStopException("In the end couldn't find another free node !!");
                }
            }
            newReplicaNodes.put (shardNbr, targetNode);
        }

        // 3. Build brick allocation
        List<BrickId> brickIds = new ArrayList<>();
        int shardNumber = 0;
        for (int j = 0; j < currentNbShards; j++) {
            Node brickNode = newReplicaNodes.get(shardNumber);
            String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume;
            brickIds.add (BrickId.fromNodeAndPath (brickNode, path));

            shardNumber++;
        }
        return brickIds;
    }

    public static List<BrickId> buildNewVolumeBrickAllocation(Volume volume, CommandContext context, Map<Node, NodeStatus> nodesStatus, Set<Node> activeNodes, RuntimeLayout rl) throws NodeStatusException {

        int targetNbrBricks = rl.getTargetNbrBricks();
        int targetNbrReplicas = rl.getTargetNbrReplicas();
        int targetNbrShards = rl.getTargetNbrShards();

        // 1. build per node brick counter
        Map<Node, Integer> nodesBrickCount = new HashMap<>();
        for (Node node : activeNodes) {

            NodeStatus nodeStatus = nodesStatus.get(node);

            Map<String, Object> nodeInfo = nodeStatus.getNodeInformation(node);

            Integer nodeBrickCount =  (Integer) nodeInfo.get("brick_count");
            nodesBrickCount.put(node, Objects.requireNonNullElse(nodeBrickCount, 0));
        }
        List<Node> sortedNodes = new ArrayList<>(activeNodes);
        sortedNodes.sort(Comparator.comparing(nodesBrickCount::get));

        // 2. Build brick allocation, assemble replicas continuously
        context.info ("    - Allocating " + targetNbrShards + " shards / " + targetNbrReplicas + " replicas = " + targetNbrBricks + " bricks");

        String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");

        // 3. Build brick allocation
        List<BrickId> brickIds = new ArrayList<>();
        for (int i = 0; i < targetNbrShards; i++) {
            for (int j = 0; j < targetNbrReplicas; j++) {
                Node brickNode = sortedNodes.remove(0);
                String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume;
                brickIds.add (BrickId.fromNodeAndPath (brickNode, path));
            }
        }
        return brickIds;
    }
}
