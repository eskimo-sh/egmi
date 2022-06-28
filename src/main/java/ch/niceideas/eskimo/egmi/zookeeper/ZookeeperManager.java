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

package ch.niceideas.eskimo.egmi.zookeeper;

import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class ZookeeperManager implements Closeable {

    private static final Logger logger = Logger.getLogger(ZookeeperManager.class);

    private final int zookeeperSessionTimeout;

    private ZooKeeper zooKeeper;

    public ZookeeperManager(final String zookeepersUrl, final int zookeeperSessionTimeout, final ElectionProcess.ProcessNodeWatcher processNodeWatcher) throws IOException {
        this.zookeeperSessionTimeout = zookeeperSessionTimeout;
        zooKeeper = new ZooKeeper(zookeepersUrl, zookeeperSessionTimeout, processNodeWatcher);
    }

    public void setData (final String nodePath, final byte[] data) {
        try {
            zooKeeper.setData(nodePath, data, -1);
        } catch (KeeperException | InterruptedException e) {
            logger.error (e, e);
            throw new IllegalStateException(e);
        }
    }

    public byte[] getData (final String nodePath, final boolean watch) {
        try {
            return zooKeeper.getData(nodePath, watch, null);
        } catch (KeeperException | InterruptedException e) {
            logger.error (e, e);
            throw new IllegalStateException(e);
        }
    }

    public String getOrCreateNode(final String node) {
        return getOrCreateNode(node, false, false, false);
    }

    public String getOrCreateNode(final String node, final boolean watch, final boolean ephemeral, final boolean sequential) {
        String createdNodePath;
        try {

            final Stat nodeStat =  zooKeeper.exists(node, watch);

            if(nodeStat == null) {
                createdNodePath = zooKeeper.create(
                        node, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        (ephemeral ? (sequential ? CreateMode.EPHEMERAL_SEQUENTIAL: CreateMode.EPHEMERAL) : CreateMode.PERSISTENT));
                if (watch) {
                    zooKeeper.exists(createdNodePath, watch);
                }
            } else {
                createdNodePath = node;
            }

        } catch (KeeperException | InterruptedException e) {
            logger.error (e, e);
            throw new IllegalStateException(e);
        }

        return createdNodePath;
    }

    public boolean watchNode(final String node, final boolean watch) {

        boolean watched = false;
        try {
            final Stat nodeStat =  zooKeeper.exists(node, watch);

            if(nodeStat != null) {
                watched = true;
            }

        } catch (KeeperException | InterruptedException e) {
            logger.error (e, e);
            throw new IllegalStateException(e);
        }

        return watched;
    }

    public List<String> getChildren(final String node, final boolean watch) {

        List<String> childNodes;

        try {
            childNodes = zooKeeper.getChildren(node, watch);
        } catch (KeeperException | InterruptedException e) {
            logger.error (e, e);
            throw new IllegalStateException(e);
        }

        return childNodes;
    }

    @Override
    public void close() throws IOException {
        try {
            zooKeeper.close(zookeeperSessionTimeout);
        } catch (InterruptedException e) {
            logger.error (e, e);
            throw new IOException(e);
        }
    }
}