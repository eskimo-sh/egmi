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
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeCreate;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeStart;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class NoVolume extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NoVolume.class);

    private Date date;
    private final String volume;

    @Override
    public String getProblemId() {
        return "NO_VOLUME-" + volume;
    }

    @Override
    public String getProblemType() {
        return "No Volume";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {

        JSONObject volumeInfo = newStatus.getVolumeInfo(volume);
        if (volumeInfo == null) {
            return true;
        }

        String status = volumeInfo.getString("status");
        return StringUtils.isNotBlank(status) && status.equals("NO VOLUME");
    }

    @Override
    public final int getPriority() {
        return 5;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionStopException {

        context.info ("- Solving " + getProblemId());

        Set<String> configuredVolumes = context.getConfiguredVolumes();

        // 1. If volume is not a managed volume, there's nothing to do, after a while, just remove it from runtime volume
        if (!configuredVolumes.contains(volume)) {

            context.info ("  + Volume " + volume + " is not a managed volume. ");

            String noVolumeTimeoutSecondsString = context.getEnvironmentProperty("problem.noVolume.timeout");
            if (StringUtils.isBlank(noVolumeTimeoutSecondsString)) {
                throw new IllegalArgumentException();
            }
            long noVolumeTimeout = Long.parseLong(noVolumeTimeoutSecondsString) * 1000;

            if (System.currentTimeMillis() - noVolumeTimeout > date.getTime()) {

                context.info ("    - Volume " + volume + " exceeded timeout of " + noVolumeTimeoutSecondsString + " seconds. Removing from tracked volumes");
                removeFromRuntimeVolumes(volume, context);
                return true;
            }

            return false;
        }

        // 2. If Volume is a managed volume, needs to be created
        else {

            context.info ("  + Volume " + volume + " IS a managed volume. Attempting creation ...");

            try {

                // 2.1 Get up Nodes in cluster sorted by number of bricks
                return createVolume(volume, glusterRemoteManager, context);

            } catch (GlusterRemoteException | NodeStatusException e) {
                logger.error (e, e);
                return false;
            }
        }
    }

    public static boolean createVolume(String volume, GlusterRemoteManager glusterRemoteManager, CommandContext context) throws GlusterRemoteException, NodeStatusException, ResolutionStopException {
        Map<String, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

        Set<String> activeNodes = getActiveConnectedNodes(nodesStatus);

        if (activeNodes.size() == 0) {
            context.info ("    !! no active node. skipping");
            return false;
        }

        RuntimeLayout rl = new RuntimeLayout(context, activeNodes);
        int targetNbrReplicas = rl.getTargetNbrReplicas();

        List<BrickId> brickIds = BrickAllocationHelper.buildNewVolumeBrickAllocation(
                volume, context, nodesStatus, activeNodes, rl);

        String lastNode = brickIds.stream().map(BrickId::getNode).findAny().get();

        context.info ("    - Bricks are " + brickIds.stream().map(BrickId::toString).collect(Collectors.joining(", ")));

        // 2.3 Call Volume create command
        executeSimpleOperation(new GlusterVolumeCreate(context.getHttpClient(), volume, targetNbrReplicas, brickIds), context, lastNode);

        // 2.4 Call Volume start command
        executeSimpleOperation(new GlusterVolumeStart(context.getHttpClient(), volume), context, lastNode);
        
        return true;
    }


    public static void removeFromRuntimeVolumes(String volume, RuntimeSettingsOwner context) throws ResolutionStopException {

        try {
            context.updateSettingsAtomically(runtimeConfig -> {
                String savedVolumesString = runtimeConfig.getValueForPathAsString("discovered-volumes");
                Set<String> savedVolumes = new HashSet<>(Arrays.asList(savedVolumesString.split(",")));
                savedVolumes.remove(volume);

                savedVolumesString = String.join(",", savedVolumes);
                runtimeConfig.setValueForPath("discovered-volumes", savedVolumesString);
                return runtimeConfig;
            });

        } catch (ManagementException e) {
            logger.error (e, e);
            throw new ResolutionStopException(e);
        }
    }
}
