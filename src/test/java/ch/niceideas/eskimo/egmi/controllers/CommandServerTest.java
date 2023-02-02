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

import ch.niceideas.common.utils.ProcessHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandServerTest {

    private CommandServer commandServer;
    private ProcessHelper processHelper;

    private final List<String> commandList = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        processHelper = new ProcessHelper() {

            @Override
            public String exec(String[] cmd, boolean throwExceptions) {
                commandList.add(String.join(" ", cmd));
                return "OK";
            }
        };

        commandServer = new CommandServer();
        commandServer.setProcessHelper(processHelper);
    }

    @Test
    public void testPing() {
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        String result = commandServer.execute(httpResp, "ping", "marathon.registry", "-c 1");

        assertEquals ("OK", result);

        assertEquals("/bin/ping -c 1 marathon.registry", String.join ("\n", commandList));
    }

    @Test
    public void testForceRemoveVolumeBricks() {
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        String result = commandServer.execute(httpResp, "force-remove-volume-bricks", "test_volume", "127.0.0.1");

        assertEquals ("OK", result);

        assertEquals("/usr/local/sbin/__force-remove-volume-bricks.sh test_volume 127.0.0.1", String.join ("\n", commandList));
    }

    @Test
    public void testForceRemoveBrick() {
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        String result = commandServer.execute(httpResp, "force-remove-brick", "/tmp/test", "127.0.0.1");

        assertEquals ("OK", result);

        assertEquals("/usr/local/sbin/__force-remove-brick.sh /tmp/test 127.0.0.1", String.join ("\n", commandList));
    }

    @Test
    public void testGlusterCommand() {
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        String result = commandServer.execute(httpResp, "volume", "replace-brick",
                "spark_data 192.168.10.72:/var/lib/gluster/volume_bricks/spark_data " +
                        "192.168.10.74:/var/lib/gluster/volume_bricks/spark_data " +
                        "commit force --mode=script");

        assertEquals ("OK", result);

        assertEquals("/usr/sbin/gluster --mode=script volume replace-brick spark_data 192.168.10.72:/var/lib/gluster/volume_bricks/spark_data 192.168.10.74:/var/lib/gluster/volume_bricks/spark_data commit force --mode=script", String.join ("\n", commandList));
    }
}
