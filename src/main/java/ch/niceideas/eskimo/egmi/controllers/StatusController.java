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

package ch.niceideas.eskimo.egmi.controllers;

import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.utils.ReturnStatusHelper;
import ch.niceideas.eskimo.egmi.zookeeper.ZookeeperService;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class StatusController {

    private static final Logger logger = Logger.getLogger(ManagementService.class);

    @Autowired
    private ManagementService managementService;

    @Autowired
    private ZookeeperService zookeeperService;

    @Value("${master.redirect.URLPattern:#{null}}")
    private String redirectURLPattern;

    @GetMapping("/get-status")
    @ResponseBody
    public String getStatus() {

        try {

            SystemStatus systemStatus = managementService.getSystemStatus();

            if (systemStatus == null) {
                return ReturnStatusHelper.createClearStatus("init", false);
            }

            return systemStatus.getFormattedValue();

        } catch (ManagementException e) {
            logger.debug(e.getCause(), e.getCause());
            return ReturnStatusHelper.createErrorStatus ((Exception)e.getCause());

        }
    }

    @GetMapping("/get-master")
    @ResponseBody
    public String getMaster() {
        return ReturnStatusHelper.createOKStatus(map -> map.putAll(new HashMap<>(){{
            put ("master", zookeeperService.isMaster());
            put ("master_url", resolveMasterUrl (redirectURLPattern, zookeeperService.getMasterHostname()));
        }}));
    }

    private String resolveMasterUrl(String redirectURLPattern, String masterHostname) {
        String retString = redirectURLPattern;
        if (redirectURLPattern.contains("{MASTER_NODE}")) {
            retString = retString.replace("{MASTER_NODE}", masterHostname);
        }
        if (redirectURLPattern.contains("{MASTER_NODE_NAME}")) {
            retString = retString.replace("{MASTER_NODE_NAME}", masterHostname.replace(".", "-"));
        }
        return retString;
    }
}
