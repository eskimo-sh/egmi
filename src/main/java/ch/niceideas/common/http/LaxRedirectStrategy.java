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

package ch.niceideas.common.http;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

import java.net.URI;

public class LaxRedirectStrategy extends DefaultRedirectStrategy implements RedirectStrategy {

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)  {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        int statusCode = response.getStatusLine().getStatusCode();
        switch(statusCode) {
            case 301:
            case 307:
            case 302:
            case 308:
            case 303:
                return true;
            case 304:
            case 305:
            case 306:
            default:
                return false;
        }
    }

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        URI uri = this.getLocationURI(request, response, context);
        String method = request.getRequestLine().getMethod();
        if(method.equalsIgnoreCase("HEAD")) {
            return new HttpHead(uri);
        } else if(method.equalsIgnoreCase("GET")) {
            return new HttpGet(uri);
        } else {
            int status = response.getStatusLine().getStatusCode();
            HttpUriRequest toReturn = null;
            if(status == 307 || status == 308) {
                toReturn = RequestBuilder.copy(request).setUri(uri).build();
                toReturn.removeHeaders("Content-Length"); //Workaround for an apparent bug in HttpClient
            } else {
                toReturn = new HttpGet(uri);
            }
            return toReturn;
        }
    }
}
