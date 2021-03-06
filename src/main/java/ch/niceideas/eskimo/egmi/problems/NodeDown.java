/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@AllArgsConstructor
public class NodeDown extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NodeDown.class);

    private final Date date;
    private final String volume;
    private final String host;

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
                .anyMatch(brickInfo -> brickInfo.getString("id").contains(host));
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
                Map<String, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

                // 1.1 Find all nodes in Nodes Status not being KO
                Set<String> activeNodes = getActiveConnectedNodes(nodesStatus);

                if (activeNodes.contains(host)) {
                    context.info ("  + Node " + host + " is back up");
                    return true;
                }

                if (activeNodes.isEmpty()) {
                    context.info ("  !! All nodes are down.");
                    return false;
                }

                Map<BrickId, String> nodeBricks = nodesStatus.get(activeNodes.stream().findFirst().get()).getNodeBricksAndVolumes(host);

                // 2. if the node doesn't run any brick and is not a managed node (not configured) just remove it from runtime node
                if (nodeBricks == null || nodeBricks.isEmpty()) {

                    if (context.getConfiguredNodes().contains(host)) {
                        context.info ("  + Node " + host + " doesn't contain any volume but is a managed node. skipping.");
                        return false;

                    } else {

                        context.info ("  + Node " + host + " doesn't contain any volume and is not a managed node. removing from tracked nodes.");
                        removefromRuntimeNodes(context);
                        return true;
                    }
                }


                // 3. if the node runs brick, consider them lost, attempt to move them elsewhere
                // for every of them
                else {

                    if (!handleNodeDownBricks(volume, host, context, nodesStatus, activeNodes, nodeBricks)) {
                        return false;
                    }
                }

                return true;
            }

            return false;

        //} catch (GlusterRemoteException | NodeStatusException e) {
        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
    }

    public static boolean handleNodeDownBricks(String volume, String host, CommandContext context, Map<String, NodeStatus> nodesStatus, Set<String> activeNodes, Map<BrickId, String> nodeBricks) throws NodeStatusException, ResolutionStopException, ResolutionSkipException {
        for (BrickId brickId : nodeBricks.keySet()) {

            String brickVolume = nodeBricks.get(brickId);

            // only fixing the volume for which I was reported
            if (brickVolume.equals(volume) && brickId.getNode().equals(host)) {

                context.info ("  + Handling Brick " + brickId);

                VolumeInformation volumeInformation = nodesStatus.get(activeNodes.stream().findFirst().get()).getVolumeInformation(brickVolume);

                // 3.1 if the volume is replicated, there should be a replica of this brick,
                if (volumeInformation.isReplicated()) {

                    context.info ("    - Brick " + brickId + " is replicated. Can proceed further");

                    Map<BrickId, BrickInformation> brickInformations =
                            nodesStatus.get(activeNodes.stream().findFirst().get()).getVolumeBricksInformation(volume);

                    // 3.1.1 If there is enough space to create this replica elsewhere remove it and create a brick elsewhere
                    // use "replace-brick" command
                    // e.g. gluster volume replace-brick r2 Server1:/home/gfs/r2_0 Server1:/home/gfs/r2_5 commit force

                    Set<String> brickNodes = brickInformations.keySet().stream().map(BrickId::getNode).collect(Collectors.toSet());
                    Set<String> candidateNodes = new HashSet<>(activeNodes);
                    candidateNodes.removeAll(brickNodes);

                    Set<String> replicaNodes = listBrickReplicaNodes(volume, brickId, volumeInformation, brickInformations, context);

                    // gluster commands need to be run on a node running a brick
                    String executionNode = findExecutionNode(brickId, brickInformations, replicaNodes, activeNodes);

                    if (candidateNodes.size() > 0) {

                        // find node where there is no brick yet
                        String targetNode = findFreeTargetNode(activeNodes, brickInformations);

                        String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");
                        String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume;
                        BrickId newBrickId = new BrickId(targetNode, path);

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

                            String targetNode = candidateNodes.stream().findAny().get();

                            // count how many bricks this node is already running for this volume
                            int counter = (int) (brickInformations.keySet().stream()
                                    .filter(otherBrickId -> otherBrickId.getNode().equals(targetNode))
                                    .count() + 1);

                            String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");
                            String path = volumePath + (volumePath.endsWith("/") ? "" : "/") + volume + "_" + counter;
                            BrickId newBrickId = new BrickId(targetNode, path);

                            context.info ("      + Replacing Brick on BUSY node " + brickId + " with " + newBrickId);
                            executeSimpleOperation(new GlusterVolumeReplaceBrick(context.getHttpClient(), volume, brickId, newBrickId), context, executionNode);
                        }

                        else {

                            // 3.1.3 If there is no way to add a new brick anywhere, reduce replication and remove all required bricks
                            int currentNbReplicas = Integer.parseInt(StringUtils.isBlank(volumeInformation.getNbReplicas()) ? "1" : volumeInformation.getNbReplicas());
                            int currentNbShards = Integer.parseInt(volumeInformation.getNbShards());

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

    private void removefromRuntimeNodes(CommandContext context) throws ResolutionStopException {
        try {

            context.updateSettingsAtomically(runtimeConfig -> {
                String savedNodesString = runtimeConfig.getValueForPathAsString("discovered-nodes");
                Set<String> savedNodes = new HashSet<>(Arrays.asList(savedNodesString.split(",")));
                savedNodes.remove(host);

                savedNodesString = String.join(",", savedNodes);
                runtimeConfig.setValueForPath("discovered-nodes", savedNodesString);
                return runtimeConfig;
            });

        } catch (ManagementException e) {
            logger.error (e, e);
            throw new ResolutionStopException(e);
        }
    }

    private static String findFreeTargetNode(Set<String> activeNodes, Map<BrickId, BrickInformation> brickInformations) throws ResolutionSkipException {
        String targetNode = null;
        for (String candidateNode : activeNodes) {
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
        if (StringUtils.isBlank(targetNode)) {
            throw new ResolutionSkipException("Impossible to find a free node. this shouldn't have happened.");
        }
        return targetNode;
    }

    public static String findExecutionNode(BrickId brickId, Map<BrickId, BrickInformation> brickInformations, Set<String> replicaNodes, Set<String> activeNodes) throws ResolutionSkipException {
        String executionNode = brickInformations.keySet().stream()
                .map(BrickId::getNode)
                .filter(replicaNodes::contains)
                .filter(activeNodes::contains)
                .findAny().orElse(null);
        if (StringUtils.isBlank(executionNode)) {
            throw new ResolutionSkipException("Impossible to find a node running a replica of brick " + brickId);
        }
        return executionNode;
    }

    private static Set<String> listBrickReplicaNodes (String volume, BrickId brickId, VolumeInformation volumeInformation, Map<BrickId, BrickInformation> brickInformations, CommandContext context)
            throws ResolutionStopException, ResolutionSkipException{
        if (StringUtils.isBlank(volumeInformation.getNbReplicas())) {
            throw new ResolutionStopException("Cannot get volume " + volume + " number of replicas");
        }
        int nbrReplicas = Integer.parseInt(volumeInformation.getNbReplicas())
                + (StringUtils.isBlank(volumeInformation.getNbArbiters()) ? 0 : Integer.parseInt(volumeInformation.getNbArbiters()));

        // 3.1.2.1 find where are the nodes with replicates (adjacent nodes)
        BrickInformation brickInformation = brickInformations.get(brickId);
        if (brickInformation.getNumber() == null) {
            throw new ResolutionStopException("Cannot get brick " + brickId + " number");
        }
        int brickNbr = brickInformation.getNumber();
        int replicaSetFirstBrickNbr = brickNbr - ((brickNbr % nbrReplicas) == 0 ? nbrReplicas : (brickNbr % nbrReplicas)) + 1;
        int replicaSetLastBrickNbr = replicaSetFirstBrickNbr + (nbrReplicas - 1);

        context.info ("      + Brick " + brickId + " is among replica set [" + replicaSetFirstBrickNbr + "-" + replicaSetLastBrickNbr + "]");

        Set<String> replicaNodes = new HashSet<>();
        for (BrickId otherBrickId : brickInformations.keySet()) {
            BrickInformation brickInformationOther = brickInformations.get(otherBrickId);
            if (brickInformationOther.getNumber() == null) {
                throw new ResolutionSkipException("Couldn't get brick number of brick " + otherBrickId);
            }
            int otherBrickNbr = brickInformationOther.getNumber();
            // is the brick part of the replica set ?
            if (otherBrickNbr >= replicaSetFirstBrickNbr && otherBrickNbr <= replicaSetLastBrickNbr) {
                replicaNodes.add(otherBrickId.getNode());
            }
        }

        return replicaNodes;
    }


}
