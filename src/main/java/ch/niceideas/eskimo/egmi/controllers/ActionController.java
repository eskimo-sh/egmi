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

package ch.niceideas.eskimo.egmi.controllers;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.management.ActionException;
import ch.niceideas.eskimo.egmi.management.ActionService;
import ch.niceideas.eskimo.egmi.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class ActionController {

    private static final Logger logger = Logger.getLogger(ActionController.class);

    @Autowired
    private ActionService actionService;

    @GetMapping("/remove-volume")
    @ResponseBody
    public String deleteNode (@RequestParam(name="volume") String volume) {
        if (StringUtils.isBlank(volume)) return ReturnStatusHelper.createErrorStatus("Passed volume is empty").getFormattedValue();
        return doAction (() -> actionService.deleteVolume (volume));
    }

    @GetMapping("/stop-volume")
    @ResponseBody
    public String stopNode (@RequestParam(name="volume") String volume) {
        if (StringUtils.isBlank(volume)) return ReturnStatusHelper.createErrorStatus("Passed volume is empty").getFormattedValue();
        return doAction (() -> actionService.stopVolume (volume));
    }

    @GetMapping("/start-volume")
    @ResponseBody
    public String startNode (@RequestParam(name="volume") String volume) {
        if (StringUtils.isBlank(volume)) return ReturnStatusHelper.createErrorStatus("Passed volume is empty").getFormattedValue();
        return doAction (() -> actionService.startVolume (volume));
    }

    @PostMapping("/volume")
    @ResponseBody
    public String addVolume (@RequestParam(name="volume") String volume) {
        if (StringUtils.isBlank(volume)) return ReturnStatusHelper.createErrorStatus("Passed volume is empty").getFormattedValue();
        return doAction (() -> actionService.addVolume (volume));
    }

    @PostMapping("/node")
    @ResponseBody
    public String addNode (@RequestParam(name="node") String node) {
        if (StringUtils.isBlank(node)) return ReturnStatusHelper.createErrorStatus("Passed node is empty").getFormattedValue();
        return doAction (() -> actionService.addNode (node));
    }

    private String doAction (Action action) {
        try {
            action.run();
            return ReturnStatusHelper.createOKStatus().getFormattedValue();
        } catch (ActionException e) {
            logger.debug(e, e);
            return ReturnStatusHelper.createErrorStatus (e).getFormattedValue();
        }
    }

    private interface Action {
        void run () throws ActionException;
    }
}
