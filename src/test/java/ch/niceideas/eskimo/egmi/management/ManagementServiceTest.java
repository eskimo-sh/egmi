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

package ch.niceideas.eskimo.egmi.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ManagementServiceTest {

    private ManagementService ms = null;

    @BeforeEach
    public void setUp() throws Exception {
        ms = new ManagementService();
    }

    @Test
    public void testComputeNumberBricks_LOG_DISPATCH() throws Exception {

        ms.targetNumberBricksString = "LOG_DISPATCH";
        ms.defaultNumberReplica = 3;

        // Testing 1 node
        ms.configuredNodes = String.join (",", "a ".repeat(1).trim().split (" "));
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(2).trim().split (" "));
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(3).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(4).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(5).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(6).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(10).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(20).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(50).trim().split (" "));
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(100).trim().split (" "));
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());
    }

    @Test
    public void testComputeNumberBricks_ALL() throws Exception {

        ms.targetNumberBricksString = "ALL";
        ms.defaultNumberReplica = 3;

        // Testing 1 node
        ms.configuredNodes = String.join (",", "a ".repeat(1).trim().split (" "));
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(2).trim().split (" "));
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(3).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(4).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(5).trim().split (" "));
        assertEquals (4, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(6).trim().split (" "));
        assertEquals (6, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(10).trim().split (" "));
        assertEquals (9, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(20).trim().split (" "));
        assertEquals (18, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(50).trim().split (" "));
        assertEquals (48, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.configuredNodes = String.join (",", "a ".repeat(100).trim().split (" "));
        assertEquals (99, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());
    }

    @Test
    public void testComputeNumberBricks_FIXED() throws Exception {

        ms.defaultNumberReplica = 5;

        // Testing 1 node
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(1).trim().split (" "));
        assertEquals (1, ms.getTargetNumberOfBricks());
        assertEquals (1, ms.getTargetNumberOfReplicas());

        // Testing 2 nodes
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(2).trim().split (" "));
        assertEquals (2, ms.getTargetNumberOfBricks());
        assertEquals (2, ms.getTargetNumberOfReplicas());

        // Testing 3 nodes
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(3).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 4 nodes
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(4).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 5 nodes
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(5).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 6 nodes
        ms.targetNumberBricksString = "3";
        ms.configuredNodes = String.join (",", "a ".repeat(6).trim().split (" "));
        assertEquals (3, ms.getTargetNumberOfBricks());
        assertEquals (3, ms.getTargetNumberOfReplicas());

        // Testing 10 nodes
        ms.targetNumberBricksString = "10";
        ms.configuredNodes = String.join (",", "a ".repeat(10).trim().split (" "));
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 20 nodes
        ms.targetNumberBricksString = "10";
        ms.configuredNodes = String.join (",", "a ".repeat(20).trim().split (" "));
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 50 nodes
        ms.targetNumberBricksString = "10";
        ms.configuredNodes = String.join (",", "a ".repeat(50).trim().split (" "));
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());

        // Testing 100 nodes
        ms.targetNumberBricksString = "10";
        ms.configuredNodes = String.join (",", "a ".repeat(100).trim().split (" "));
        assertEquals (10, ms.getTargetNumberOfBricks());
        assertEquals (5, ms.getTargetNumberOfReplicas());
    }

}
