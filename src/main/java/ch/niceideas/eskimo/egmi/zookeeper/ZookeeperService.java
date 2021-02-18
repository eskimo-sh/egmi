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

import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ZookeeperService {

    private static final Logger logger = Logger.getLogger(ZookeeperService.class);

    private final AtomicBoolean master = new AtomicBoolean(false);

    private final AtomicReference<String> masterTracker = new AtomicReference<>();

    private boolean stopping = false;

    @PreDestroy
    public void destroy() {
        stopping = true;
    }

    public ZookeeperService(
            @Value("${hostname:#{null}}") String hostname,
            @Value("${zookeeper.urls:#{null}}") String zookeeperUrls,
            @Value("${zookeeper.sessionTimeout:#{5000}}") int zookeeperSessionTimeout,
            @Value("${master}") String forceMasterFlag,
            @Value("${data}") boolean dataNode) {

        if (StringUtils.isNotBlank(forceMasterFlag)) {
            boolean masterFlag = Boolean.parseBoolean(forceMasterFlag);
            logger.warn ("Forcing master=" + masterFlag);
            masterTracker.set("[UNKNOWN]");
            master.set(masterFlag);
            return;
        }

        if (StringUtils.isBlank(hostname)) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
                logger.warn ("No hostname configured. Assuming " + hostname);
            } catch (UnknownHostException e) {
                logger.error (e, e);
                throw new IllegalStateException(e);
            }
        }
        final String effHostname = hostname;

        if (StringUtils.isBlank(zookeeperUrls)) {
            logger.warn ("Running in STANDALONE mode. Assuming master !");
            masterTracker.set(hostname);
            master.set(true);
            return;
        }

        if (zookeeperSessionTimeout <= 0) {
            zookeeperSessionTimeout = 5000;
        }
        final int timeout = zookeeperSessionTimeout;

        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;

        }).submit( () -> {

            while (!stopping) {

                try {
                    ElectionProcess electionProcess = new ElectionProcess(effHostname, dataNode, zookeeperUrls, timeout, new ElectionCallback() {
                        @Override
                        public void onMasterChanged(String masterHostname) {
                            masterTracker.set(masterHostname);
                        }

                        @Override
                        public void onMasterGained() {
                            master.set(true);
                        }
                    });
                    electionProcess.run();

                } catch (Exception e) {
                    logger.error (e, e);
                    logger.warn ("Waiting 10 seconds and retrying");
                    try {
                        //noinspection BusyWait
                        Thread.sleep(10000);
                    } catch (InterruptedException interruptedException) {
                        logger.debug (e, e);
                    }
                } finally {
                    master.set(false);
                }
            }
        });
    }

    public boolean isMaster() {
        return master.get();
    }

    public String getMasterHostname() {
        return masterTracker.get();
    }

    public String getConfiguredNodes() {
        // FIXME

        // 1. Query zookeeper for all data nodes

        // 2. register listener to be notified of changes in data nodes

        return null;
    }
}
