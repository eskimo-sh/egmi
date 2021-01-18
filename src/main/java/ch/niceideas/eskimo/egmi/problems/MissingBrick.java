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
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeAddBrick;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.json.JSONArray;

import java.util.*;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class MissingBrick extends AbstractProblem implements Problem{

    private static final Logger logger = Logger.getLogger(MissingBrick.class);

    private Date date;
    private final String volume;
    private final int targetNbrBricks;
    private final int nbrBricks;

    @Override
    public String getProblemId() {
        return "MISSING_BRICK-" + volume + "-" + (targetNbrBricks - nbrBricks) ;
    }

    @Override
    public String getProblemType() {
        return "Missing Brick";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        JSONArray brickArray = newStatus.getBrickArray(volume);
        return brickArray!= null && brickArray.length() <= targetNbrBricks;
    }

    @Override
    public final int getPriority() {
        return 6;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionSkipException, ResolutionStopException {

        context.info ("- Solving " + getProblemId());

        try {

            // 1. Get active nodes
            Map<String, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

            Set<String> activeNodes = getActiveNodes(nodesStatus);

            if (activeNodes.size() == 0) {
                context.info ("  !! no active node. skipping");
                return false;
            }

            // 2. Get volume and bricks information

            // 2.1 get volume info
            String firstActiveNode = activeNodes.stream().findFirst().get();
            VolumeInformation volumeInformation = nodesStatus.get(firstActiveNode).getVolumeInformation(volume);
            if (volumeInformation == null || StringUtils.isBlank(volumeInformation.getStatus())) {
                context.info ("  !! Cannot get volume information");
                throw new ResolutionSkipException();
            } else if (!volumeInformation.getStatus().equals("OK")) {
                context.info ("  !! Volume status is not OK ");
                throw new ResolutionSkipException();
            }

            // 2.2 Get existing bricks layout

            Set<String> volumeNodes = nodesStatus.get(firstActiveNode).getVolumeNodes(volume);

            Map<BrickId, BrickInformation> brickInformations =
                    nodesStatus.get(firstActiveNode).getVolumeBricksInformation(volume);
            int currentNbReplicas = Integer.parseInt(StringUtils.isBlank(volumeInformation.getNbReplicas()) ? "1" : volumeInformation.getNbReplicas());
            int currentNbShards = Integer.parseInt(volumeInformation.getNbShards());
            int currentNbrBricks = currentNbShards * currentNbReplicas;

            RuntimeLayout rl = new RuntimeLayout(context, activeNodes);
            int targetNbrReplicas = Math.max (rl.getTargetNbrReplicas(), currentNbReplicas);
            int targetNbrShards = Math.max (rl.getTargetNbrShards(), currentNbShards);
            int targetNbrBricks = targetNbrReplicas * targetNbrShards;

            if (currentNbrBricks >= targetNbrBricks) {
                context.info ("  + Already have maximum allocatable Nb Bricks. Skipping");
                return false;
            }

            if (    currentNbReplicas >= activeNodes.size() / currentNbShards
                 && currentNbShards >= activeNodes.size() / currentNbReplicas) {
                context.info ("  + Cannot add any new brick (already " + currentNbReplicas+ " replicas / " + currentNbShards + " shards - on " + activeNodes.size() + " nodes). Skipping");
                return false;
            }

            // 3. Add missing bricks
            String volumePath = context.getEnvironmentProperty("target.glusterVolumes.path");

            List<String> sortedNodes = new ArrayList<>(activeNodes);
            sortedNodes.sort(new NodeBrickNumberComparator (nodesStatus.get(firstActiveNode)));

            // 3.1 If enough room Add replicas until target number of replicas is reached

            // find out how many replicas can be added
            int nbrReplicasToCreate = (activeNodes.size() - currentNbrBricks) / currentNbShards;
            if (nbrReplicasToCreate > 0 && currentNbReplicas < targetNbrReplicas) {

                context.info ("  + Adding one replica to every shard");

                List<BrickId> brickIds = BrickAllocationHelper.buildNewReplicasBrickAllocation(
                        volume, brickInformations, currentNbReplicas, currentNbShards, volumePath, sortedNodes);

                String lastNode = brickIds.stream().map(BrickId::getNode).findAny().get();

                context.info ("    - Bricks are " + brickIds.stream().map(BrickId::toString).collect(Collectors.joining(", ")));

                // 3.1.3 Call Add Brick command
                executeSimpleOperation(new GlusterVolumeAddBrick(context.getHttpClient(), volume, currentNbReplicas + 1, brickIds), context, lastNode);

                return true;
            }

            // 3.2 If still enough room, add shards (and their replicas) until target Number of Bricks is reached

            // in this case I want free nodes, makes no sense to add shards on busy nodes
            // just get currentNbReplicas free nodes

            List<String> freeNodes = sortedNodes.stream()
                    .filter(node -> !volumeNodes.contains(node))
                    .collect(Collectors.toList());
            if (freeNodes.size() >= currentNbReplicas && currentNbShards < targetNbrShards) {

                context.info ("  + Adding one shard with replicas");

                List<BrickId> brickIds = BrickAllocationHelper.buildNewShardBrickAllocation(
                        volume, brickInformations, currentNbReplicas, volumePath, sortedNodes);

                String lastNode = brickIds.stream().map(BrickId::getNode).findAny().get();

                context.info ("    - Bricks are " + brickIds.stream().map(BrickId::toString).collect(Collectors.joining(", ")));

                executeSimpleOperation(new GlusterVolumeAddBrick(context.getHttpClient(), volume, currentNbReplicas, brickIds), context, lastNode);

                return true;
            }

            context.info ("  + Cannot add anything new with [current " + currentNbReplicas + " replicas / " + currentNbShards + " shards ] " +
                    "on [target " + targetNbrReplicas+ " replicas / " + targetNbrShards + " shards] with " + activeNodes.size() + " active nodes");

        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }

        return false;
    }


}
