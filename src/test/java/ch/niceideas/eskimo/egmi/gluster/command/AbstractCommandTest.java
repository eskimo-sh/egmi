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

import ch.niceideas.common.exceptions.CommonRTException;
import ch.niceideas.common.http.HttpClient;
import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.common.http.HttpClientResponse;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.problems.CommandContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractCommandTest {


    protected HttpClient mockClient = null;
    protected final AtomicReference<String> url = new AtomicReference<>();
    protected final AtomicReference<String> response = new AtomicReference<>();

    protected CommandContext context;

    @BeforeEach
    public void setUp() {

        ClassicHttpResponse respProxy = (ClassicHttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ClassicHttpResponse.class}, (proxy, method, args) -> {

            switch (method.getName()) {
                case "getEntity":
                    return new StringEntity(response.get());
                case "getCode":
                    return 200;
                case "getReasonPhrase":
                    return "Ok";
                case "getHeaders":
                    return new Header[0];
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });

        mockClient = new HttpClient() {

            @Override
            public HttpClientResponse sendRequest(String url) {
                AbstractCommandTest.this.url.set(url);
                try {
                    return new HttpClientResponse(respProxy, "127.0.0.1");
                } catch (HttpClientException e) {
                    throw new CommonRTException(e);
                }
            }
        };


        ManagementService ms = new ManagementService(false) {

            @Override
            public String getContextRoot() {
                return "";
            }
        };

        context = new CommandContext(mockClient, 12345, ms);
    }


}
