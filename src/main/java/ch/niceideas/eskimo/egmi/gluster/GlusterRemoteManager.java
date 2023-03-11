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

package ch.niceideas.eskimo.egmi.gluster;

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterPoolList;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeInfo;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterVolumeStatus;
import ch.niceideas.eskimo.egmi.gluster.command.Ping;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterPoolListResult;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeInfoResult;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeStatusResult;
import ch.niceideas.eskimo.egmi.gluster.command.result.PingResult;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.model.BrickId;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.problems.CommandContext;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class GlusterRemoteManager {

    private static final Logger logger = Logger.getLogger(GlusterRemoteManager.class);

    private static final Pattern IP_ADDRESS_REGEX = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");

    @Value("${remote.egmi.port}")
    private int glusterCommandServerPort = 18999;

    @Autowired
    private ManagementService managementService;

    @Bean
    public HttpClient httpClient() {
        return new HttpClient();
    }

    @Autowired
    private HttpClient httpClient;

    /** For Tests */
    public void setHttpClient (HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    public void setManagementService (ManagementService managementService) {
        this.managementService = managementService;
    }

    public Map<Node, NodeStatus> getAllNodeStatus() throws GlusterRemoteException {

        Map<Node, NodeStatus> retMap = new HashMap<>();

        try {
            for (Node node : managementService.getAllNodes()) {
                try {
                    retMap.put(node, getNodeStatus(node));
                } catch (GlusterRemoteException e) {
                    logger.debug (e, e);
                    logger.error (e.getCompleteMessage());
                }
            }
        } catch (ManagementException e) {
            logger.error (e, e);
            throw new GlusterRemoteException(e);
        }

        return retMap;
    }

    public String resolve (String hostname, Node node) throws IOException, HttpClientException {

        if (StringUtils.isBlank(hostname)) {
            return "";
        }

        if (hostname.trim().equalsIgnoreCase("localhost")) {
            return node.getAddress();
        }

        Matcher ipAddressMatcher = IP_ADDRESS_REGEX.matcher(hostname);
        if (ipAddressMatcher.matches()) {
            return hostname;
        }

        CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

        PingResult pingResult = new Ping(httpClient, hostname).execute(node, context);
        return pingResult.getResolvedIP();
    }

    public NodeStatus getNodeStatus (Node node) throws GlusterRemoteException {

        NodeStatus status = new NodeStatus("{}");

        try {

            CommandContext context = new CommandContext(httpClient, glusterCommandServerPort, managementService);

            // 1. get peer list
            GlusterPoolList poolListCmd = new GlusterPoolList(httpClient);
            GlusterPoolListResult poolResult = poolListCmd.execute(node, context);
            for (int i = 0; i < poolResult.size(); i++) {
                status.setValueForPath("peers." + i + ".uid", poolResult.getUid(i));
                status.setValueForPath("peers." + i + ".hostname", resolve (poolResult.getHostname(i), node));
                status.setValueForPath("peers." + i + ".state", poolResult.getState(i));
            }

            // 2. get volume information
            GlusterVolumeInfo volumeInfoCmd = new GlusterVolumeInfo(httpClient);
            GlusterVolumeInfoResult volumeInfo = volumeInfoCmd.execute(node, context);

            // 3. Fetch brick details
            GlusterVolumeStatus volumeStatusCmd = new GlusterVolumeStatus(httpClient, volumeInfo);
            GlusterVolumeStatusResult volumeStatus = volumeStatusCmd.execute(node, context);

            // 4. Add it all to brick status
            int counter = 0;
            for (String volume : volumeInfo.getAllVolumes()) {

                status.setValueForPath("volumes." + counter + ".name", volume);

                Set<String> volumeOptions = volumeInfo.getVolumeReconfiguredOptions(volume);
                if (volumeOptions != null && !volumeOptions.isEmpty()) {

                    for (String option: volumeOptions) {

                        String[] parsedOption = option.split(":");
                        if (parsedOption.length == 2) {

                            String optionKey = parsedOption[0].trim().replace(".", "__");
                            String optionValue = parsedOption[1].trim();

                            status.setValueForPath("volumes." + counter + ".options." + optionKey, optionValue);
                        }
                    }
                }

                volumeInfo.feedVolumeInfoInStatus(status, volume, counter);

                if (volumeInfo.hasBrickIds(volume)) {

                    Map<Integer, BrickId> bricks = volumeInfo.getNumberedBrickIds(volume);

                    for (Integer brickNumber : bricks.keySet()) {

                        BrickId brickId = bricks.get(brickNumber);

                        String brickPrefix = "volumes." + counter + ".bricks." + (brickNumber - 1);
                        status.setValueForPath(brickPrefix + ".number", brickNumber);
                        status.setValueForPath(brickPrefix + ".node", brickId.getNode().getAddress());
                        status.setValueForPath(brickPrefix + ".path", brickId.getPath());

                        volumeStatus.feedVolumeStatusInStatus (status, counter, (brickNumber - 1), brickId.toString());
                    }
                }

                counter++;
            }

        } catch (HttpClientException | IOException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
            throw new GlusterRemoteException(e);
        }

        return status;
    }
}
