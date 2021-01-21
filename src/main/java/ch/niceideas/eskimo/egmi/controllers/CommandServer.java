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

import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;

@Controller
public class CommandServer {

    private static final Logger logger = Logger.getLogger(CommandServer.class);

    private ProcessHelper processHelper = new ProcessHelper();

    /** For tests */
    public void setProcessHelper (ProcessHelper processHelper) {
        this.processHelper = processHelper;
    }

    @GetMapping(path="/command", produces="text/plain")
    @ResponseBody
    public String execute (
            HttpServletResponse response,
            @RequestParam(name="command") String command,
            @RequestParam(name="subcommand") String subcommand,
            @RequestParam(name="options") String options) {

        logger.info ("About to execute command: " + command + " - subcommand: " + subcommand + " - options: " + options);

        if (StringUtils.isBlank(command)) {
            return "";
        }

        String commandLine;
        switch (command) {
            case "force-remove-peer":
                commandLine = String.format("/usr/local/sbin/__force-remove-peer.sh %s", options);
                break;
            case "force-remove-brick":
                commandLine = String.format("/usr/local/sbin/__force-remove-brick.sh %s %s", subcommand, options);
                break;
            case "ping":
                commandLine = String.format("/bin/ping %s %s", options, subcommand);
                break;
            default:
                commandLine = String.format("/usr/sbin/gluster %s %s %s", command, subcommand, options);
                break;
        }

        String[] processCommand = commandLine.split(" ");
        try {
            return processHelper.exec(processCommand, true);

        } catch (ProcessHelper.ProcessHelperException  e) {
            logger.error (e, e);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return e.getCompleteMessage();
        }
    }
}
