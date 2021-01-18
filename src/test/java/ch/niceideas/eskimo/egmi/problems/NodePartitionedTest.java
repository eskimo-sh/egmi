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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class NodePartitionedTest extends AbstractProblemTest {

    private NodePartitioned problem;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        problem = new NodePartitioned(new Date(), "192.168.10.71");
    }

    @Test
    public void testRecognize() throws Exception {
        assertTrue (problem.recognize(systemStatus));
    }

    @Test
    public void testSolve() throws Exception {

        try {
            problem.solve(grm, new CommandContext(mockClient, 1234, ms));
            fail ("NodeResolutionStopException expected");
        } catch (ResolutionStopException e) {
            assertEquals("! Failed to confirm peer addition in 5 attempts.", e.getMessage());
        }

        assertEquals("192.168.10.71:1234/command?command=volume&subcommand=stop&options=test2%20--mode=script\n" +
                "192.168.10.71:1234/command?command=force-remove-brick&subcommand=test2&options=192.168.10.71\n" +
                "192.168.10.72:1234/command?command=force-remove-brick&subcommand=test2&options=192.168.10.72\n" +
                "192.168.10.71:1234/command?command=volume&subcommand=delete&options=test2%20--mode=script\n" +
                "192.168.10.71:1234/command?command=volume&subcommand=stop&options=test1%20--mode=script\n" +
                "192.168.10.71:1234/command?command=force-remove-brick&subcommand=test1&options=192.168.10.71\n" +
                "192.168.10.71:1234/command?command=volume&subcommand=delete&options=test1%20--mode=script\n" +
                "192.168.10.72:1234/command?command=peer&subcommand=detach&options=192.168.10.71\n" +
                "192.168.10.73:1234/command?command=peer&subcommand=probe&options=192.168.10.71\n" +
                "192.168.10.71:1234/command?command=pool&subcommand=list&options=\n" +
                "192.168.10.71:1234/command?command=pool&subcommand=list&options=\n" +
                "192.168.10.71:1234/command?command=pool&subcommand=list&options=\n" +
                "192.168.10.71:1234/command?command=pool&subcommand=list&options=\n" +
                "192.168.10.71:1234/command?command=pool&subcommand=list&options=", String.join("\n", urls));
    }
}
