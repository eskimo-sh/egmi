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

import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.*;
import ch.niceideas.eskimo.egmi.gluster.command.result.SimpleOperationResult;
import ch.niceideas.eskimo.egmi.management.GraphPartitionDetector;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.*;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@AllArgsConstructor
public class NodePartitioned extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NodePartitioned.class);

    private Date date;
    private final Node host;


    @Override
    public String getProblemId() {
        return "NODE_PARTITIONED-" + host;
    }

    @Override
    public String getProblemType() {
        return "Node Partitioned";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        String status = newStatus.getNodeStatus(host);

        return StringUtils.isBlank(status) || status.equals("PARTITIONED");
    }

    @Override
    public final int getPriority() {
        return 0;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionStopException {

        context.info ("- Solving " + getProblemId());

        try {

            // 1. find out if node can be added to a larger cluster
            Map<Node, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

            // 1.1 Find all nodes in Nodes Status not being KO
            Set<Node> activeNodes = getActiveNodes(nodesStatus);

            if (!activeNodes.contains(host)) {
                context.info ("  !! Node " + host + " is not active");
                return false;
            }

            // 1.2 Find those that are not partitioned => candidates
            // 1.3 if none, find those that have the highest connection count => candidates
            Map<Node, GraphPartitionDetector.GraphNode> nodeNetwork = GraphPartitionDetector.buildNodeGraph(activeNodes, nodesStatus);

            Map<Node, Integer> counters = GraphPartitionDetector.buildPeerTimesVolumeCounters(activeNodes, nodeNetwork, nodesStatus);

            final AtomicInteger highestCount = new AtomicInteger(Integer.MIN_VALUE);
            for (Node current : counters.keySet()) {
                if (counters.get(current) > highestCount.get()) {
                    highestCount.set (counters.get(current));
                }
            }

            Set<Node> hostPeers = GraphPartitionDetector.buildPeerNetwork(nodeNetwork, host);

            Set<Node> candidates = counters.keySet().stream()
                    .filter (curHost -> counters.get(curHost) == highestCount.get())
                    .filter (curHost -> !hostPeers.contains(curHost))
                    .collect(Collectors.toSet());

            context.info ("  + Candidates " + candidates.stream()
                    .map(Node::getAddress)
                    .collect(Collectors.joining(", ")));


            // 2. If no candidates are found, stop here (return false, no resolution possible)
            if (candidates.isEmpty()) {
                return false;
            }


            // 3. Find out if current cluster is different from best candidate cluster

            // find a candidate with no intersection with own peers
            Set<Node> isolatedCandidates = candidates.stream()
                    .map(candidate -> new Pair<>(candidate, GraphPartitionDetector.buildPeerNetwork(nodeNetwork, candidate)))
                    .filter(pair -> {
                        pair.getValue().retainAll(hostPeers);
                        return pair.getValue().size() == 0;
                    })
                    .map(Pair::getKey)
                    .collect(Collectors.toSet());

            context.info ("  + Isolated Candidates " + String.join(", ", candidates.stream()
                    .map(Node::getAddress)
                    .collect(Collectors.joining(", "))));

            // 3.1 If cluster is the same, stop here (return false, no resolution possible)
            if (isolatedCandidates.size() == 0) {
                return false;
            }

            // 3.2. If cluster is different, then This node should be added to the new cluster

            if (hostPeers.size() > 1) {

                context.info ("  + Need to disconnect " + host + " from " + (hostPeers.size() - 1) + " peers");


                // 4. Handle disconnection from current cluster - volume parts
                //    remove all volumes it is currently holding

                NodeStatus nodeStatus = nodesStatus.get(host);
                Map<BrickId, Volume> nodeBricksAndVolumes = nodeStatus.getNodeBricksAndVolumes(host);
                Set<Volume> nodeVolumes = new HashSet<>(nodeBricksAndVolumes.values());

                for (Volume volume : nodeVolumes) {

                    NodeVolumeInformation volumeInfo = nodeStatus.getVolumeInformation(volume);
                    context.info ("    - Forcing removal of volume " + volume + " !");

                    Set<Node> volumeNodes = nodeStatus.getVolumeNodes(volume);

                    // 4.2.1 Stop volume
                    // only if volume is started
                    String volumeStatus = volumeInfo.getStatus();
                    if (StringUtils.isNotBlank(volumeStatus) && volumeStatus.equals("OK")) {
                        executeSimpleOperation(new GlusterVolumeStop(context.getHttpClient(), volume), context, host);
                    }

                    // 4.2.2 Force delete bricks
                    for (Node brickNode : volumeNodes) {
                        executeSimpleOperation(new ForceRemoveVolumeBricks(context.getHttpClient(), volume, brickNode), context, brickNode);
                    }

                    // 4.2.3 Delete Volume
                    try {
                        executeSimpleOperation(new GlusterVolumeDelete(context.getHttpClient(), volume), context, host);
                    } catch (ResolutionStopException e) {
                        context.error("!!! This is a big deal. The volume bricks have been deleted already and the volume is not recoverable");
                        throw new ResolutionStopException(e);
                    }
                }


                // 5. Handle disconnection from cluster, peer parts
                boolean peerDetached = false;
                for (Node peer : hostPeers.stream().filter(peer -> !peer.equals(host)).collect(Collectors.toSet()) ) {

                    context.info ("    - Detaching peer " + peer);

                    try {

                        // 5.1 Do removal other way around, remove current host from peers
                        GlusterPeerDetach peerDetachForth = new GlusterPeerDetach(context.getHttpClient(), host);
                        SimpleOperationResult peerDetachForthResult = peerDetachForth.execute(peer, context);
                        if (!peerDetachForthResult.isSuccess()) {
                            context.error ("        ! Detaching " + host + " from " + peer + " failed. Trying next.");
                            logger.error (peerDetachForthResult.getMessage());
                        } else {
                            peerDetached = true;
                            break;
                        }

                    } catch (HttpClientException e) {
                        logger.debug (e, e);
                        logger.error (e.getCompleteMessage());
                        throw new ResolutionStopException(e.getCompleteMessage(), e);
                    }
                }

                // 5.2 Stop if host couldn't be detached from peers
                if (!peerDetached) {
                    context.error ("    - Detaching host failed! ");
                    throw new ResolutionStopException();
                }

            }

            context.info ("  + Attaching to new cluster");

            for (Node candidate : isolatedCandidates) {

                context.info ("    - Trying to attach to candidate " + candidate);

                // 6. Try adding host to new cluster

                // 6.1 Add host to new best candidate peers
                try {
                    GlusterPeerProbe peerProbe = new GlusterPeerProbe(context.getHttpClient(), host);
                    SimpleOperationResult peerProbeResult = peerProbe.execute(candidate, context);
                    if (!peerProbeResult.isSuccess()) {
                        context.error ("      ! Attaching " + host + " to " + candidate + " failed. Trying next candidate if available.");
                        logger.error (peerProbeResult.getMessage());
                        continue;
                    } else {
                        context.info("        + peer probe " + host + " on " + candidate + " succeeded");
                    }

                    // 7. Check host is added
                    checkHostInPeerPool(context, candidate, host);

                    return true;

                } catch (HttpClientException | IOException e) {
                    logger.debug (e, e);
                    logger.error (e.getMessage());
                    throw new ResolutionStopException(e);
                }

            }

        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }

        context.error ("  + Failed to attach to any candidate.");
        throw new ResolutionStopException("Failed to attach to any candidate.");
    }

}
