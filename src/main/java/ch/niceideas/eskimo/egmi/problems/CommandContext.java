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

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class CommandContext implements ResolutionLogger, RuntimeSettingsOwner, ConfigurationOwner {

    private final HttpClient httpClient;
    private final int glusterCommandServerPort;
    private final ResolutionLogger logger;
    private final RuntimeSettingsOwner rtSettingsOwner;
    private final ConfigurationOwner configOwner;

    public CommandContext (HttpClient httpClient, int glusterCommandServerPort, ManagementService managementService) {
        this (httpClient, glusterCommandServerPort, managementService, managementService, managementService);
    }

    public void info(String s) {
        logger.info(s);
    }

    public void error(String s) {
        logger.error(s);
    }

    public void error(Exception e) {
        logger.error(e);
    }

    @Override
    public void updateSettingsAtomically (ManagementService.SettingsUpdater updater) throws ManagementException {
        rtSettingsOwner.updateSettingsAtomically(updater);
    }

    @Override
    public int getTargetNumberOfBricks() {
        return rtSettingsOwner.getTargetNumberOfBricks();
    }

    @Override
    public int getTargetNumberOfReplicas() {
        return rtSettingsOwner.getTargetNumberOfReplicas();
    }

    @Override
    public Set<String> getConfiguredNodes() {
        return configOwner.getConfiguredNodes();
    }

    @Override
    public Set<String> getConfiguredVolumes() {
        return configOwner.getConfiguredVolumes();
    }

    @Override
    public String getEnvironmentProperty (String property) {
        return configOwner.getEnvironmentProperty(property);
    }

    @Override
    public String getContextRoot() {
        return configOwner.getContextRoot();
    }
}
