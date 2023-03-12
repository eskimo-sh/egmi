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

package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeRemoveBrick;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeReplaceBrick;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@AllArgsConstructor
public class NodeDown extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NodeDown.class);

    private final Date date;
    private final Volume volume;
    private final Node host;

    @Override
    public String getProblemId() {
        return "NODE_DOWN-" + volume + "-" + host;
    }

    @Override
    public String getProblemType() {
        return "Vol. Node Down";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        JSONObject nodeInfo = newStatus.getNodeInfo(host);
        if (nodeInfo == null) {
            return true;
        }

        String status = nodeInfo.getString("status");
        if (StringUtils.isNotBlank(status) && !status.equals("KO")) {
            return false;
        }

        JSONArray brickArray = newStatus.getBrickArray(volume);
        if (brickArray == null) {
            return false;
        }

        return IntStream.range(0, brickArray.length())
                .mapToObj(brickArray::getJSONObject)
                .anyMatch(brickInfo -> brickInfo.getString("id").contains(host.getAddress()));
    }

    @Override
    public final int getPriority() {
        return 3;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionSkipException, ResolutionStopException {

        try {

            // 0. after a while, when the node is considered definitely dead
            String nodeDeadTimeoutSecondsString = context.getEnvironmentProperty("problem.nodeDown.timeout");
            if (StringUtils.isBlank(nodeDeadTimeoutSecondsString)) {
                throw new IllegalArgumentException();
            }
            long nodeDeadTimeout = Long.parseLong(nodeDeadTimeoutSecondsString) * 1000;

            long age = System.currentTimeMillis() - date.getTime();
            context.info ("- Solving " + getProblemId() + " (age ms = " + age + ")");

            if (age >= nodeDeadTimeout) {

                // 1. Find out if node is still down
                Map<Node, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

                // 1.1 Find all nodes in Nodes Status not being KO
                Set<Node> activeNodes = getActiveConnectedNodes(nodesStatus);

                if (activeNodes.contains(host)) {
                    context.info ("  + Node " + host + " is back up");
                    return true;
                }

                if (activeNodes.isEmpty()) {
                    context.info ("  !! All nodes are down.");
                    return false;
                }

                Map<BrickId, Volume> nodeBricks = nodesStatus.get(
                            getFirstNode(activeNodes).orElseThrow(IllegalStateException::new))
                        .getNodeBricksAndVolumes(host);

                // 2. if the node runs brick, consider them lost, attempt to move them elsewhere
                //    for every of them
                if (nodeBricks != null && !nodeBricks.isEmpty()) {

                    return handleNodeDownBricks(volume, host, context, nodesStatus, activeNodes, nodeBricks);
                }
            }

            return false;

        //} catch (GlusterRemoteException | NodeStatusException e) {
        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
    }

    public static boolean handleNodeDownBricks(Volume volume, Node host, CommandContext context, Map<Node, NodeStatus> nodesStatus, Set<Node> activeNodes, Map<BrickId, Volume> nodeBricks) throws NodeStatusException, ResolutionStopException, ResolutionSkipException {
        for (BrickId brickId : nodeBricks.keySet()) {

            Volume brickVolume = nodeBricks.get(brickId);

            // only fixing the volume for which I was reported
            if (brickVolume.equals(volume) && host.equals(brickId.getNode())) {

                context.info ("  + Handling Brick " + brickId);

                NodeVolumeInformation nodeVolumeInformation = nodesStatus.get(activeNodes.stream()
                            .findFirst()
                            .orElseThrow(IllegalStateException::new))
                        .getVolumeInformation(brickVolume);

                // 3.1 if the volume is replicated, there should be a replica of this brick,
                if (nodeVolumeInformation.isReplicated()) {

                    context.info ("    - Brick " + brickId + " is replicated. Can proceed further");

                    Map<BrickId, NodeBrickInformation> brickInformations =
                            nodesStatus.get(activeNodes.stream().
                                        findFirst().
                                        orElseThrow(IllegalStateException::new))
                                    .getVolumeBricksInformation(volume);

                    // 3.1.1 If there is enough space to create this replica elsewhere remove it and create a brick elsewhere
                    // use "replace-brick" command
                    // e.g. gluster volume replace-brick r2 Server1:/home/gfs/r2_0 Server1:/home/gfs/r2_5 commit force

                    Set<Node> brickNodes = brickInformations.keySet().stream().map(BrickId::getNode).collect(Collectors.toSet());
                    Set<Node> candidateNodes = new HashSet<>(activeNodes);
                    candidateNodes.removeAll(brickNodes);

                    Set<Node> replicaNodes = listBrickReplicaNodes(volume, brickId, nodeVolumeInformation, brickInformations, context);

                    // gluster commands need to be run on a node running a brick
                    Node executionNode = findExecutionNode(brickId, brickInformations, replicaNodes, activeNodes);

                    if (candidateNodes.size() > 0) {

                        // find node where there is no brick yet
                        Node targetNode = findFreeTargetNode(activeNodes, brickInformations);

                        String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");
                        String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume;
                        BrickId newBrickId = BrickId.fromNodeAndPath(targetNode, path);

                        context.info ("      + Replacing Brick on FREE node " + brickId + " with " + newBrickId);
                        executeSimpleOperation(new GlusterVolumeReplaceBrick(context.getHttpClient(), volume, brickId, newBrickId), context, executionNode);

                        //if this commands fails, I might need to force restart and not stop execution
                    }

                    else {

                        // 3.1.2 If there is not enough space to recreate the replica elsewhere, see if I can add a replica on a
                        // node running a shard already

                        // 3.1.2.2 Find if a node NOT running a replicate is active

                        candidateNodes = new HashSet<>(activeNodes);
                        candidateNodes.removeAll(replicaNodes);

                        // 3.1.2.3 if such a node is found, replace the brick there
                        if (candidateNodes.size() > 0 ) {

                            Node targetNode = candidateNodes.stream().findAny().get();

                            // count how many bricks this node is already running for this volume
                            int counter = (int) (brickInformations.keySet().stream()
                                    .filter(otherBrickId -> otherBrickId.getNode().equals(targetNode))
                                    .count() + 1);

                            String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");
                            String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume + "_" + counter;
                            BrickId newBrickId = BrickId.fromNodeAndPath(targetNode, path);

                            context.info ("      + Replacing Brick on BUSY node " + brickId + " with " + newBrickId);
                            executeSimpleOperation(new GlusterVolumeReplaceBrick(context.getHttpClient(), volume, brickId, newBrickId), context, executionNode);
                        }

                        else {

                            // 3.1.3 If there is no way to add a new brick anywhere, reduce replication and remove all required bricks
                            int currentNbReplicas = Integer.parseInt(StringUtils.isBlank(nodeVolumeInformation.getNbReplicas()) ? "1" : nodeVolumeInformation.getNbReplicas());
                            int currentNbShards = Integer.parseInt(nodeVolumeInformation.getNbShards());

                            List<BrickId> removedBrickIds = BrickAllocationHelper.buildReplicaUnallocation (brickInformations, host, currentNbReplicas, currentNbShards);

                            context.info ("      !!! Removing Bricks " + removedBrickIds);
                            executeSimpleOperation(new GlusterVolumeRemoveBrick(context.getHttpClient(), volume, currentNbReplicas - 1, removedBrickIds), context, executionNode);
                        }
                    }
                }

                // 3.2 if the volume was not replicated, see what is possible
                // (worst case, recreate whole volume - LOG HUGE WARNING FOR DATA LOSS)
                // TODO DON'T DO IT FOR NOW. THIS NEEDS TO BE CHALLENGED. Not Sure I should actually handle this
                else {

                    context.info ("    - " + brickId + " is NOT replicated. Skipping for now");
                    context.info ("    - " + brickId + " PROBLEM WON'T BE SOLVED!");
                    return false;
                }
            }
        }
        return true;
    }

    private static Node findFreeTargetNode(Set<Node> activeNodes, Map<BrickId, NodeBrickInformation> brickInformations) throws ResolutionSkipException {
        Node targetNode = null;
        for (Node candidateNode : activeNodes) {
            boolean found = false;
            for (BrickId brickIdOther : brickInformations.keySet()) {
                if (brickIdOther.getNode().equals(candidateNode)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                targetNode = candidateNode;
                break;
            }
        }
        if (targetNode == null) {
            throw new ResolutionSkipException("Impossible to find a free node. this shouldn't have happened.");
        }
        return targetNode;
    }

    public static Node findExecutionNode(BrickId brickId, Map<BrickId, NodeBrickInformation> brickInformations, Set<Node> replicaNodes, Set<Node> activeNodes) throws ResolutionSkipException {
        Node executionNode = brickInformations.keySet().stream()
                .map(BrickId::getNode)
                .filter(replicaNodes::contains)
                .filter(activeNodes::contains)
                .findAny().orElse(null);
        if (executionNode == null) {
            throw new ResolutionSkipException("Impossible to find a node running a replica of brick " + brickId);
        }
        return executionNode;
    }

    private static Set<Node> listBrickReplicaNodes (Volume volume, BrickId brickId, NodeVolumeInformation nodeVolumeInformation, Map<BrickId, NodeBrickInformation> brickInformations, CommandContext context)
            throws ResolutionStopException, ResolutionSkipException{
        if (StringUtils.isBlank(nodeVolumeInformation.getNbReplicas())) {
            throw new ResolutionStopException("Cannot get volume " + volume + " number of replicas");
        }
        int nbrReplicas = Integer.parseInt(nodeVolumeInformation.getNbReplicas())
                + (StringUtils.isBlank(nodeVolumeInformation.getNbArbiters()) ? 0 : Integer.parseInt(nodeVolumeInformation.getNbArbiters()));

        // 3.1.2.1 find where are the nodes with replicates (adjacent nodes)
        NodeBrickInformation nodeBrickInformation = brickInformations.get(brickId);
        if (nodeBrickInformation.getNumber() == null) {
            throw new ResolutionStopException("Cannot get brick " + brickId + " number");
        }
        int brickNbr = nodeBrickInformation.getNumber();
        int replicaSetFirstBrickNbr = brickNbr - ((brickNbr % nbrReplicas) == 0 ? nbrReplicas : (brickNbr % nbrReplicas)) + 1;
        int replicaSetLastBrickNbr = replicaSetFirstBrickNbr + (nbrReplicas - 1);

        context.info ("      + Brick " + brickId + " is among replica set [" + replicaSetFirstBrickNbr + "-" + replicaSetLastBrickNbr + "]");

        Set<Node> replicaNodes = new HashSet<>();
        for (BrickId otherBrickId : brickInformations.keySet()) {
            NodeBrickInformation nodeBrickInformationOther = brickInformations.get(otherBrickId);
            if (nodeBrickInformationOther.getNumber() == null) {
                throw new ResolutionSkipException("Couldn't get brick number of brick " + otherBrickId);
            }
            int otherBrickNbr = nodeBrickInformationOther.getNumber();
            // is the brick part of the replica set ?
            if (otherBrickNbr >= replicaSetFirstBrickNbr && otherBrickNbr <= replicaSetLastBrickNbr) {
                replicaNodes.add(otherBrickId.getNode());
            }
        }

        return replicaNodes;
    }


}
