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
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeStart;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeStatusResult;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@AllArgsConstructor
public class VolumeNotStarted extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(VolumeNotStarted.class);

    private Date date;
    private final Volume volume;

    @Override
    public String getProblemId() {
        return "VOLUME_DOWN-" + volume;
    }

    @Override
    public String getProblemType() {
        return "Volume Down";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        String status = newStatus.getVolumeStatus(volume);
        return StringUtils.isNotBlank(status) && status.trim().contains("NOT STARTED");
    }

    @Override
    public final int getPriority() {
        return 4;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionStopException {

        context.info ("- Solving " + getProblemId());

        try {

            // 1. Confirm volume is not started
            Node host = null;

            Map<Node, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();
            for (Node node : nodesStatus.keySet()) {

                NodeStatus nodeStatus = nodesStatus.get(node);
                if (nodeStatus != null) {

                    NodeVolumeInformation nodeVolumeInfo = nodeStatus.getVolumeInformation(volume);
                    String volStatus = nodeVolumeInfo.getStatus();

                    if (StringUtils.isNotBlank(volStatus) && volStatus.equals(GlusterVolumeStatusResult.VOL_NOT_STARTED_FLAG)) {
                        host = node;
                        break;
                    }
                }
            }

            if (host != null) {

                // 2. Start volume
                context.info ("  + Starting volume " + volume);
                executeSimpleOperation(new GlusterVolumeStart(context.getHttpClient(), volume), context, host);
            }

            return true;

        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
    }
}
