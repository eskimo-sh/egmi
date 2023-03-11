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

package ch.niceideas.eskimo.egmi.gluster.command;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeInfoResult;
import ch.niceideas.eskimo.egmi.model.BrickId;
import ch.niceideas.eskimo.egmi.model.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GlusterVolumeInfoTest extends AbstractCommandTest {

    @Test
    public void testCommand() throws Exception {

        response.set(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("command/GlusterVolumeInfoResult.txt")));

        GlusterVolumeInfo command = new GlusterVolumeInfo(mockClient);
        GlusterVolumeInfoResult result = command.execute(Node.from("127.0.0.1"), context);
        assertNotNull (result);

        String volumes = String.join(",", result.getAllVolumes());
        assertEquals ("test2,test1", volumes);

        assertEquals ("192.168.10.71:/var/lib/gluster/volume_bricks/test2_bis_1," +
                      "192.168.10.72:/var/lib/gluster/volume_bricks/test2_bis_2",
                String.join (",", result.getBrickIds("test2").stream().map(BrickId::toString).collect(Collectors.toSet())));

        Set<String> test1Options = result.getVolumeReconfiguredOptions("test2");
        assertNotNull(test1Options);
        assertEquals(3, test1Options.size());

        List<String> sortedOptions = new ArrayList<>(test1Options);
        sortedOptions.sort(Comparator.naturalOrder());
        assertEquals("nfs.disable: on", sortedOptions.get(0));
        assertEquals("performance.client-io-threads: off", sortedOptions.get(1));
        assertEquals("transport.address-family: inet", sortedOptions.get(2));
    }


}
