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
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterPeerDetach;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterPeerProbe;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@AllArgsConstructor
public class NodeInconsistent extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NodeInconsistent.class);

    private Date date;
    private final Node host;
    private final Node other;


    @Override
    public String getProblemId() {
        return "NODE_INCONSISTENT-" + host + "-" + other;
    }

    @Override
    public String getProblemType() {
        return "Node Inconsistent";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        String status = newStatus.getNodeStatus(host);
        return StringUtils.isBlank(status) || status.equals("INCONSISTENT");
    }

    @Override
    public final int getPriority() {
        return 1;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionStopException, ResolutionSkipException {

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

            // removing inconsistent host
            activeNodes.remove(host);

            // 2. Remove all node bricks
            Node activeNode = activeNodes.stream().findFirst().orElseThrow(IllegalStateException::new);
            Map<BrickId, Volume> nodeBricks = nodesStatus.get(activeNode).getNodeBricksAndVolumes(host);
            for (Volume volume : nodeBricks.values()) {
                if (!NodeDown.handleNodeDownBricks(volume, host, context, nodesStatus, activeNodes, nodeBricks)) {
                    return false;
                }
            }

            // 3. Force remove host from peer reporting it
            context.info ("  + Force detaching " + host + " from " + other + " peer pool.");
            executeSimpleOperation(new GlusterPeerDetach(context.getHttpClient(), host), context, other);

            // 4. Add it again
            context.info ("  + Re-adding " + host + " to " + other + " peer pool.");
            executeSimpleOperation(new GlusterPeerProbe(context.getHttpClient(), host), context, other);

            try {
                // 7. Ensure it is properly available
                checkHostInPeerPool(context, other, host);

            } catch (HttpClientException | IOException e) {
                logger.debug (e, e);
                logger.error (e.getMessage());
                throw new ResolutionStopException(e);
            }

            return true;

        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
    }

}
