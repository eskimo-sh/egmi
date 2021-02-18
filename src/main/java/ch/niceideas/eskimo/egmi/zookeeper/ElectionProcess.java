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

package ch.niceideas.eskimo.egmi.zookeeper;

import org.apache.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class ElectionProcess implements Runnable, Closeable {

    private static final Logger logger = Logger.getLogger(ElectionProcess.class);

    private static final String EGMI_ROOT_NODE = "/egmi";
    private static final String LEADER_ELECTION_ROOT_NODE = "/egmi_election";
    private static final String PROCESS_NODE_PREFIX = "/p_";
    private static final String MASTER_TRACKER_NODE = "/master_id";
    private static final String DATA_NODE_FOLDER = "/data_nodes/";
    private static final String DATA_NODE = "/data";

    private final Object sessionMonitor = new Object();

    private final String id;
    private final boolean dataNode;
    private final ZookeeperManager zookeeperManager;

    private final ElectionCallback callback;

    private String processNodePath;
    private String leaderNodeWatch;

    private final ProcessNodeWatcher watcher = new ProcessNodeWatcher();


    public ElectionProcess(final String id, final boolean dataNode, final String zkURL, final int zkSessionTimeout, final ElectionCallback callback) throws IOException {
        this.dataNode = dataNode;
        this.id = id;
        this.callback = callback;
        zookeeperManager = new ZookeeperManager(zkURL, zkSessionTimeout, watcher);
    }

    private void attemptForLeaderPosition() {

        final List<String> childNodePaths = zookeeperManager.getChildren(EGMI_ROOT_NODE + LEADER_ELECTION_ROOT_NODE, false);

        Collections.sort(childNodePaths);

        int index = childNodePaths.indexOf(processNodePath.substring(processNodePath.lastIndexOf('/') + 1));
        if (index == 0) {
            logger.info("[Process: " + id + "] I am the new leader!");
            callback.onMasterGained();
            zookeeperManager.setData(EGMI_ROOT_NODE + MASTER_TRACKER_NODE, id.getBytes(StandardCharsets.UTF_8));
        } else {
            final String watchedNodeShortPath = childNodePaths.get(index - 1);

            leaderNodeWatch = EGMI_ROOT_NODE + LEADER_ELECTION_ROOT_NODE + "/" + watchedNodeShortPath;

            logger.info("[Process: " + id + "] - Setting watch on node with path: " + leaderNodeWatch);
            zookeeperManager.watchNode(leaderNodeWatch, true);
        }
    }

    @Override
    public void run() {

        logger.info("Process with id: " + id + " has started!");

        zookeeperManager.getOrCreateNode(EGMI_ROOT_NODE, false, false);

        final String electionRootNodePath = zookeeperManager.getOrCreateNode(EGMI_ROOT_NODE + LEADER_ELECTION_ROOT_NODE, false, false);
        if(electionRootNodePath == null) {
            throw new IllegalStateException("Unable to create/access leader election root node with path: " + EGMI_ROOT_NODE + LEADER_ELECTION_ROOT_NODE);
        }

        logger.info("[Process: " + id + "] Creating data node " + EGMI_ROOT_NODE + DATA_NODE_FOLDER + DATA_NODE);
        zookeeperManager.getOrCreateNode(EGMI_ROOT_NODE + DATA_NODE_FOLDER + MASTER_TRACKER_NODE, false, true);

        logger.info("[Process: " + id + "] Registering watches on data nodes folder " + EGMI_ROOT_NODE + DATA_NODE_FOLDER);
        zookeeperManager.watchNode(EGMI_ROOT_NODE + DATA_NODE_FOLDER, true);

        logger.info("[Process: " + id + "] Ensuring master tracker node exists on " + EGMI_ROOT_NODE + MASTER_TRACKER_NODE);
        zookeeperManager.getOrCreateNode(EGMI_ROOT_NODE + MASTER_TRACKER_NODE, true, false);

        logger.debug("[Process: " + id + "] Creating Process node");
        processNodePath = zookeeperManager.getOrCreateNode(electionRootNodePath + PROCESS_NODE_PREFIX, false, true);
        if(processNodePath == null) {
            throw new IllegalStateException("Unable to create/access process node with path: " + EGMI_ROOT_NODE + LEADER_ELECTION_ROOT_NODE);
        }

        logger.debug("[Process: " + id + "] Process node created with path: " + processNodePath);

        String master = new String  (zookeeperManager.getData(EGMI_ROOT_NODE + MASTER_TRACKER_NODE, true), StandardCharsets.UTF_8);
        logger.info("[Process: " + id + "] Current master is : " + master);
        callback.onMasterChanged(master);

        attemptForLeaderPosition();

        synchronized (sessionMonitor) {
            try {
                sessionMonitor.wait();
            } catch (InterruptedException e) {
                logger.error (e, e);
            } finally {
                try {
                    zookeeperManager.close();
                } catch (IOException e) {
                    logger.error (e, e);
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (sessionMonitor) {
            sessionMonitor.notify(); // end election process
        }
    }

    public class ProcessNodeWatcher implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            logger.info("[Process: " + id + "] Event received: " + event);

            final Event.EventType eventType = event.getType();

            if (Event.EventType.NodeDeleted.equals(eventType)) {

                if (event.getPath().equalsIgnoreCase(leaderNodeWatch)) {
                    attemptForLeaderPosition();
                }

            } else if (Event.EventType.NodeDataChanged.equals(eventType)
                    && event.getPath().toLowerCase().contains(MASTER_TRACKER_NODE)) {
                String newMaster = new String (zookeeperManager.getData(event.getPath(), true), StandardCharsets.UTF_8);
                logger.info("[Process: " + id + "] Master changed: " + newMaster);
                callback.onMasterChanged(newMaster);

            } else if (Event.KeeperState.Disconnected.equals(event.getState())
                    || Event.KeeperState.Expired.equals(event.getState())) {
                close();
            }
        }
    }

}
