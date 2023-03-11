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

package ch.niceideas.eskimo.egmi.gluster;

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.common.http.HttpClientResponse;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeInfoResult;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class GlusterRemoteManagerTest {

    private GlusterRemoteManager grm = null;

    protected final AtomicReference<String> response = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        grm = new GlusterRemoteManager();
    }

    @Test
    public void testBrickReprParser() {

        Matcher matcher = GlusterVolumeInfoResult.BRICKS_REPR_PARSER.matcher("1 x 2 = 2");
        assertTrue (matcher.matches());
        assertEquals ("1", matcher.group(1));
        assertEquals ("2", matcher.group(3));
        assertNull (matcher.group(5));
        assertEquals ("2", matcher.group(6));

        matcher = GlusterVolumeInfoResult.BRICKS_REPR_PARSER.matcher("2 x (2 + 1) = 6");
        assertTrue (matcher.matches());
        assertEquals ("2", matcher.group(1));
        assertEquals ("2", matcher.group(3));
        assertEquals ("1", matcher.group(5));
        assertEquals ("6", matcher.group(6));
    }

    @Test
    public void testGetNodeStatus() throws Exception {

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

        HttpClient mockClient = new HttpClient() {

            @Override
            public HttpClientResponse sendRequest(String url) throws HttpClientException {
                try {
                    //System.err.println (url);

                    //StreamUtils.getAsString(ResourceUtils.getResourceAsStream("GlusterRemoteManagerTest/runtime-config.json"))

                    switch (url) {
                        case "192.168.10.71:18999/command?command=pool&subcommand=list&options=":
                            response.set(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("GlusterRemoteManagerTest/result-pool-list.txt")));
                            break;
                        case "192.168.10.71:18999/command?command=volume&subcommand=info&options=":
                            response.set(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("GlusterRemoteManagerTest/result-volume-info.txt")));
                            break;
                        case "192.168.10.71:18999/command?command=volume&subcommand=status&options=all%20detail":
                            response.set(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("GlusterRemoteManagerTest/result-volume-status.txt")));
                            break;
                    }

                    return new HttpClientResponse(respProxy, "127.0.0.1");
                } catch (IOException e) {
                    throw new HttpClientException(e.getMessage(), e);
                }
            }
        };

        grm.setHttpClient(mockClient);


        grm.setManagementService(new ManagementService(false) {

            @Override
            public String getContextRoot() {
                return "";
            }
        });

        NodeStatus nodeStatus = grm.getNodeStatus(Node.from("192.168.10.71"));
        assertNotNull(nodeStatus);

        //System.err.println (nodeStatus.getFormattedValue());

        String expectedStatusString = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("GlusterRemoteManagerTest/NodeStatusResult.txt"));
        NodeStatus expectedStatus = new NodeStatus(expectedStatusString);

        //assertEquals(expectedStatus.getFormattedValue(), nodeStatus.getFormattedValue());
        assertTrue (expectedStatus.getJSONObject().similar(nodeStatus.getJSONObject()));

    }

}
