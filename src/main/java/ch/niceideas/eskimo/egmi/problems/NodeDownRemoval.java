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
import ch.niceideas.eskimo.egmi.gluster.command.GlusterPeerDetach;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeRemoveBrick;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeReplaceBrick;
import ch.niceideas.eskimo.egmi.management.ManagementException;
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
public class NodeDownRemoval extends AbstractProblem implements Problem {

    private static final Logger logger = Logger.getLogger(NodeDownRemoval.class);

    private final Date date;
    private final String host;

    @Override
    public String getProblemId() {
        return "NODE_DOWN-REMOVAL-" + host;
    }

    @Override
    public String getProblemType() {
        return "Vol. Node Down Removal";
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        JSONObject nodeInfo = newStatus.getNodeInfo(host);
        if (nodeInfo == null) {
            return true;
        }

        String status = nodeInfo.getString("status");
        return StringUtils.isBlank(status) || status.equals("KO");
    }

    @Override
    public final int getPriority() {
        return 4;
    }

    @Override
    public final boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) throws ResolutionSkipException, ResolutionStopException {

        try {

            // 0. after a while, when the node is considered definitely dead
            String nodeDeadTimeoutSecondsString = context.getEnvironmentProperty("problem.nodeDownRemoval.timeout");
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

                String other = activeNodes.stream().findFirst().orElseThrow(IllegalStateException::new);

                // 2. Force remove host from peer reporting it
                context.info ("  + Force detaching " + host + " from " + other + " peer pool.");
                executeSimpleOperation(new GlusterPeerDetach(context.getHttpClient(), host), context, other);

                if (context.getConfiguredNodes().contains(host)) {
                    context.info ("  + Node " + host + " doesn't contain any volume but is a managed node. skipping.");
                    return false;

                } else {

                    context.info ("  + Node " + host + " doesn't contain any volume and is not a managed node. removing from tracked nodes.");
                    removefromRuntimeNodes(context);
                    return true;
                }
            }

            return false;

        //} catch (GlusterRemoteException | NodeStatusException e) {
        } catch (GlusterRemoteException | NodeStatusException e) {
            logger.error (e, e);
            return false;
        }
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
}
