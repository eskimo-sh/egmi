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

package ch.niceideas.eskimo.egmi.management;

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.*;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.NodeStatusException;
import ch.niceideas.eskimo.egmi.model.VolumeInformation;
import ch.niceideas.eskimo.egmi.problems.AbstractProblem;
import ch.niceideas.eskimo.egmi.problems.NoVolume;
import ch.niceideas.eskimo.egmi.problems.CommandContext;
import ch.niceideas.eskimo.egmi.problems.ResolutionStopException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ActionService {

    private static final Logger logger = Logger.getLogger(ActionService.class);

    @Autowired
    private GlusterRemoteManager glusterRemoteManager;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private HttpClient httpClient;

    @Value("${remote.egmi.port}")
    private int glusterCommandServerPort = 18999;

    public void deleteVolume(String volume) throws ActionException {

        managementService.executeInLock (() -> {

            try {

                CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

                managementService.info("Deleting Volume " + volume);

                Map<String, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

                String actionNode = nodesStatus.keySet().stream()
                        .filter(node -> nodesStatus.get(node) != null && !nodesStatus.get(node).isPoolStatusError())
                        .findAny().orElseThrow(() -> new ResolutionStopException("Couldn't find any active node"));

                NodeStatus nodeStatus = nodesStatus.get(actionNode);

                VolumeInformation volumeInfo = nodeStatus.getVolumeInformation(volume);

                Set<String> volumeNodes = nodeStatus.getVolumeNodes(volume);

                // 1. Force Stop volume
                try {
                    managementService.info("  - Stopping Volume " + volume);
                    AbstractProblem.executeSimpleOperation(new GlusterVolumeStop(httpClient, volume, true), context, actionNode);
                } catch (Exception e) {
                    logger.warn("Previous error is ignored.");
                }

                // 2. Force delete bricks
                managementService.info("  - Force-removing bricks for " + volume);
                for (String brickNode : volumeNodes) {
                    managementService.info("    + Removing bricks for " + volume + " on " + brickNode);
                    AbstractProblem.executeSimpleOperation(new ForceRemoveVolumeBricks(httpClient, volume, brickNode), context, brickNode);
                }

                // 3. Delete Volume
                try {
                    managementService.info("  - Now Deleting Volume " + volume);
                    AbstractProblem.executeSimpleOperation(new GlusterVolumeDelete(httpClient, volume), context, actionNode);
                } catch (ResolutionStopException e) {
                    managementService.error("!!! This is a big deal. The volume bricks have been deleted already and the volume is not recoverable");
                    throw new ResolutionStopException(e);
                }

                // 4. Remove it from runtime config
                NoVolume.removeFromRuntimeVolumes(volume, managementService);

            } catch (ResolutionStopException | GlusterRemoteException | NodeStatusException e) {
                logger.error (e, e);
                throw new ActionException(e);
            }
        });
    }

    public void stopVolume(String volume) throws ActionException {

        managementService.executeInLock (() -> {

            try {

                managementService.info("Stopping Volume " + volume);

                CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

                List<String> lastNodes = managementService.getSystemStatus().getNodeList();
                if (lastNodes.size() <= 0) {
                    throw new ActionException("No existing peer to stop the volume on.");
                }

                // Add host to new best candidate peers
                AbstractProblem.executeSimpleOperation(new GlusterVolumeStop(httpClient, volume), context, lastNodes.get(0));

            } catch (ManagementException | ResolutionStopException e) {
                logger.error (e, e);
                throw new ActionException(e);
            }
        });
    }

    public void startVolume(String volume) throws ActionException {

        managementService.executeInLock (() -> {

            try {

                managementService.info("Starting Volume " + volume);

                CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

                List<String> lastNodes = managementService.getSystemStatus().getNodeList();
                if (lastNodes.size() <= 0) {
                    throw new ActionException("No existing peer to start the volume on.");
                }

                // Add host to new best candidate peers
                AbstractProblem.executeSimpleOperation(new GlusterVolumeStart(httpClient, volume), context, lastNodes.get(0));

            } catch (ManagementException | ResolutionStopException e) {
                logger.error (e, e);
                throw new ActionException(e);
            }
        });
    }

    public void addVolume(String volume) throws ActionException{

        managementService.executeInLock (() -> {

            try {

                managementService.info("Adding Volume " + volume);

                CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

                if (!NoVolume.createVolume(volume, glusterRemoteManager, context)) {
                    throw new ActionException("Couldn't create volume " + volume);
                }

            } catch (GlusterRemoteException | NodeStatusException| ResolutionStopException e) {
                logger.error (e, e);
                throw new ActionException(e);
            }
        });
    }

    public void addNode(String node) throws ActionException{

        managementService.executeInLock (() -> {

            try {

                managementService.info("Adding node " + node);

                CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

                List<String> lastNodes = managementService.getSystemStatus().getNodeList();
                if (lastNodes.size() <= 0) {
                    throw new ActionException("No existing peer to add the new node to.");
                }

                // Add host to new best candidate peers
                AbstractProblem.executeSimpleOperation(new GlusterPeerProbe(httpClient, node), context, lastNodes.get(0));

            } catch (ManagementException | ResolutionStopException e) {
                logger.error (e, e);
                throw new ActionException(e);
            }
        });
    }
}
