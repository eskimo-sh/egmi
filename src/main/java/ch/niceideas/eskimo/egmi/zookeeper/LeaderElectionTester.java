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

package ch.niceideas.eskimo.egmi.zookeeper;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LeaderElectionTester {

    private static final Logger logger = Logger.getLogger(LeaderElectionTester.class);

    public static void main(String[] args) throws IOException {

        if(args.length < 3) {
            logger.error("Usage: LeaderElectionTester <ID> <zkhost:port pairs> timeout");
            System.exit(2);
        }

        final String id = args[0];
        final String zkURL = args[1];
        final int zkSessionTimeout = Integer.parseInt(args[2]);

        AtomicBoolean shutDown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutDown.set(true)));

        while (!shutDown.get()) {
            ElectionProcess electionProcess = new ElectionProcess(id, true, true, zkURL, zkSessionTimeout, new ElectionCallback() {
                @Override
                public void onMasterChanged(String masterHostname) {
                    // ignored
                }

                @Override
                public void onMasterGained() {
                    // ignored
                }
            });
            electionProcess.run();
        }
    }
}
