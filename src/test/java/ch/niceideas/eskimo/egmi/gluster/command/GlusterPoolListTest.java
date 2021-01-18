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

package ch.niceideas.eskimo.egmi.gluster.command;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterPoolListResult;
import ch.niceideas.eskimo.egmi.gluster.command.result.SimpleOperationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GlusterPoolListTest extends AbstractCommandTest {

    @Test
    public void testCommand() throws Exception {

        response.set(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("command/GlusterPoolListResult.txt")));

        GlusterPoolList command = new GlusterPoolList(mockClient);
        GlusterPoolListResult result = command.execute("127.0.0.1", context);
        assertNotNull (result);

        assertEquals (4, result.size());

        assertEquals ("192.168.10.74", result.getHostname(1));
        assertEquals ("localhost", result.getHostname(3));

        assertEquals ("e4c4dadd-19b1-433c-b6e3-32a31325e4a0", result.getUid(1));
        assertEquals ("bef24025-ac9e-4ff4-8f63-d72644c5b708", result.getUid(3));

        assertEquals ("Connected", result.getState(3));
    }


}
