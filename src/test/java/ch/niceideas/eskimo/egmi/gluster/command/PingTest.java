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

package ch.niceideas.eskimo.egmi.gluster.command;

import ch.niceideas.eskimo.egmi.gluster.command.result.PingResult;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class PingTest extends AbstractCommandTest {

    @Test
    public void testRegex() {
        String pingIp =
                "PING 192.168.10.71 (192.168.10.71) 56(84) bytes of data.\n" +
                "64 bytes from 192.168.10.71: icmp_seq=1 ttl=64 time=1.33 ms\n" +
                "\n" +
                "--- 192.168.10.71 ping statistics ---\n" +
                "1 packets transmitted, 1 received, 0% packet loss, time 0ms\n" +
                "rtt min/avg/max/mdev = 1.334/1.334/1.334/0.000 ms\n";

        Matcher pingIpMatcher = PingResult.PING_RESULT_PATTERN.matcher(pingIp);
        assertTrue(pingIpMatcher.find());
        assertEquals("192.168.10.71", pingIpMatcher.group(2));

        String pingHostname =
                "PING marathon.registry (192.168.10.74) 56(84) bytes of data.\n" +
                "64 bytes from marathon.registry (192.168.10.74): icmp_seq=1 ttl=64 time=1.58 ms\n" +
                "\n" +
                "--- marathon.registry ping statistics ---\n" +
                "1 packets transmitted, 1 received, 0% packet loss, time 0ms\n" +
                "rtt min/avg/max/mdev = 1.579/1.579/1.579/0.000 ms\n";

        Matcher pingHostnameMatcher = PingResult.PING_RESULT_PATTERN.matcher(pingHostname);
        assertTrue(pingHostnameMatcher.find());
        assertEquals("192.168.10.74", pingHostnameMatcher.group(2));
    }

    @Test
    public void testCommand() throws Exception {

        response.set("PING marathon.registry (192.168.10.74) 56(84) bytes of data.\n" +
                "64 bytes from marathon.registry (192.168.10.74): icmp_seq=1 ttl=64 time=1.58 ms\n" +
                "\n" +
                "--- marathon.registry ping statistics ---\n" +
                "1 packets transmitted, 1 received, 0% packet loss, time 0ms\n" +
                "rtt min/avg/max/mdev = 1.579/1.579/1.579/0.000 ms\n");

        Ping command = new Ping(mockClient, "marathon.registry");
        PingResult result = command.execute("127.0.0.1", context);
        assertNotNull (result);

        assertEquals("192.168.10.74", result.getResolvedIP());

        assertEquals("127.0.0.1:12345/command?command=ping&subcommand=marathon.registry&options=-c%201", url.get());
    }


}
