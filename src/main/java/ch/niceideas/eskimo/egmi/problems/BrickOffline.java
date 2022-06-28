/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.egmi.gluster.command.FixStartBrick;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeStart;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class BrickOffline extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(BrickOffline.class);

    private Date date;
    private final String volume;
    private final BrickId brickId;

    @Override
    public String getProblemId() {
        return "BRICK_OFFLINE-" + volume + "-" + brickId;
    }

    @Override
    public String getProblemType() {
        return "Brick Offline";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        JSONObject brickInfo = newStatus.getBrickInfo(volume, brickId);
        return brickInfo != null
                && StringUtils.isNotBlank(brickInfo.getString("status"))
                && brickInfo.getString("status").trim().equals("OFFLINE");
    }

    @Override
    public final int getPriority() {
        return 4;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionStopException {

        try {

            // 0. after a while, when the node is considered definitely dead
            String brickOfflineTimeoutSecondsString = context.getEnvironmentProperty("problem.brickOffline.timeout");
            if (StringUtils.isBlank(brickOfflineTimeoutSecondsString)) {
                throw new IllegalArgumentException();
            }
            long brickOfflineTimeout = Long.parseLong(brickOfflineTimeoutSecondsString) * 1000;

            long age = System.currentTimeMillis() - date.getTime();
            context.info ("- Solving " + getProblemId() + " (age ms = " + age + ")");

            if (age >= brickOfflineTimeout) {

                // 1. Confirm a brick is offline
                Map<String, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

                String node = brickId.getNode();

                Set<String> activeNodes = getActiveNodes(nodesStatus);

                if (!activeNodes.contains(node)) {
                    context.info ("  !! Node " + node + " is not active");
                    return false;
                }

                NodeStatus nodeStatus = nodesStatus.get(node);
                if (nodeStatus != null) {

                    VolumeInformation nodeVolumeInfo = nodeStatus.getVolumeInformation(volume);
                    if (nodeVolumeInfo == null) {
                        context.info ("  + Couldn't find volume information for " + volume + " in node status for " + node);
                    } else {

                        String volStatus = nodeVolumeInfo.getStatus();
                        if (StringUtils.isNotBlank(volStatus) && volStatus.contains("TEMP")) {
                            context.info ("  + " + volume + " in has sttaus TEMP");

                        } else {

                            Map<BrickId, BrickInformation> nodeBricksInfo = nodeStatus.getVolumeBricksInformation(volume);
                            BrickInformation nodeBrickInfo = nodeBricksInfo.get(brickId);


                            String effStatus = nodeBrickInfo.getStatus();
                            if (effStatus == null || !effStatus.equals("OFFLINE")) {
                                context.info ("  + Brick is not reported offline anymore");

                            } else {

                                context.info("  + Force Starting volume " + volume);

                                // Start volume
                                executeSimpleOperation(new FixStartBrick(context.getHttpClient(), volume, node), context, node);

                                return true;
                            }
                        }
                    }
                }
            }

            return false;

        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
    }

    /*
    fix with
    gluster volume start test_2 force
    */
}
