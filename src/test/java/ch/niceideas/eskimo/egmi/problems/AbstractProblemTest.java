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

package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.common.http.HttpClientResponse;
import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteException;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.management.ManagementException;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.management.MessagingService;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractProblemTest {

    protected GlusterRemoteManager grm;
    protected final AtomicReference<String> response = new AtomicReference<>();
    protected HttpClient mockClient;
    protected ManagementService ms;
    protected MessagingService messagingService;
    protected final List<String> urls = new ArrayList<>();
    protected SystemStatus systemStatus;

    protected String getTestRoot() {
        return getClass().getSimpleName();
    }

    @BeforeEach
    public void setUp() throws Exception {

        ClassicHttpResponse respProxy = (ClassicHttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClassicHttpResponse.class}, (proxy, method, args) -> {

            switch (method.getName()) {
                case "getEntity":
                    return new StringEntity(response.get());

                case "getCode":
                    return 200;

                case "getReasonPhrase":
                    return "Ok";

                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });

        mockClient = new HttpClient() {

            @Override
            public HttpClientResponse sendRequest(String url) {
                urls.add(url);
                response.set("success");
                return new HttpClientResponse(respProxy, "127.0.0.1");
            }
        };

        ms = new ManagementService(false) {

            @Override
            public void saveRuntimeSetting(JsonWrapper settings) {
                // No Op
            }

            @Override
            public JsonWrapper loadRuntimeSettings() throws ManagementException {
                try {
                    return new JsonWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ManagementServiceTest/" + getTestRoot() + "/runtime-config.json")));
                } catch (IOException e) {
                    throw new ManagementException (e);
                }
            }

            @Override
            public String getEnvironmentProperty (String property) {
                switch (property) {
                    case "target.glusterVolumes.path":
                        return "/var/lib/gluster/volume_bricks/";
                    case "problem.nodeDown.timeout":
                    case "problem.brickOffline.timeout":
                        return "0";
                }
                return null;
            }

            @Override
            public String getContextRoot() {
                return "";
            }
        };
        ms.setTestConfig("192.168.10.71,192.168.10.72,192.168.10.73,192.168.10.74", "spark_eventlog,spark_data,flink_data,logstash_data,test");

        grm = new GlusterRemoteManager() {

            @Override
            public Map<String, NodeStatus> getAllNodeStatus() throws GlusterRemoteException {
                try {
                    Map<String, NodeStatus> retMap = new HashMap<>();
                    for (String node : new String[]{"192.168.10.71", "192.168.10.72", "192.168.10.73", "192.168.10.74"}) {
                        InputStream nodeStatusIs = ResourceUtils.getResourceAsStream("problems/" + getTestRoot() + "/" + node + ".json");
                        if (nodeStatusIs != null) {
                            retMap.put(node, new NodeStatus(StreamUtils.getAsString(nodeStatusIs)));
                        }
                    }
                    return retMap;
                } catch (IOException e) {
                    throw new GlusterRemoteException(e);
                }
            }
        };

        messagingService = new MessagingService(5000);

        ms.setMessagingService(messagingService);

        grm.setHttpClient(mockClient);

        ms.setGlusterRemoteManager(grm);

        systemStatus = new SystemStatus(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("problems/" + getTestRoot() + "/systemStatus.json")));
    }

}
