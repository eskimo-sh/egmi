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

package ch.niceideas.common.http;

import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A response built by the HttpClient and wrapping the underlying implementation response.
 * <p />
 * 
 * As of now, the response can be converted to a String, an InputStream or a Byte array.
 * <p />
 *
 * <b>VERY IMPORTANT : EVERY INSTANCE OF HttpClientResponse MUST BE released() to avoid thread starvation.</b>
 */
public class HttpClientResponse implements Closeable {

    private static final Logger logger = Logger.getLogger(HttpClientResponse.class);

    private final HttpEntity responseEntity;
    private final String targetHost;

    private final Map<String, String> headerMap = new HashMap<>();

    private final int responseCode;
    private final String reasonPhrase;

    private byte[] respBytes;

    public HttpClientResponse(ClassicHttpResponse response, String targetHost) throws HttpClientException {
        super();
        this.responseEntity = response.getEntity();
        this.targetHost = targetHost;

        this.responseCode = response.getCode();
        this.reasonPhrase = response.getReasonPhrase();

        for (Header header : response.getHeaders()) {
            String key = header.getName();
            String value = header.getValue();
            if (key.equalsIgnoreCase("Transfer-Encoding") && StringUtils.isNotBlank(value)) {
                if (responseEntity != null && responseEntity.isChunked()) {
                    headerMap.put("Content-Length", "" + responseEntity.getContentLength());
                } else {
                    throw new IllegalStateException(
                            "'Transfer-Encoding' header is present but no no chunked entity is available in response.");
                }
            } else {
                headerMap.put(header.getName(), header.getValue());
            }

        }

        this.resolveContent();
    }

    /**
     * 
     */
    public void release() {
        EntityUtils.consumeQuietly(responseEntity);
    }

    /**
     * @return the HTPP status code of the response
     */
    public int getStatusCode() {
        return responseCode;
    }
    
    public HttpResponseStatus getStatus() {
        return HttpResponseStatus.fromCode(getStatusCode());
    }

    public String getTargetHost() {
        return targetHost;
    }

    /**
     * @return a <String, String> map containing the header attributes.
     */
    public Map<String, String> getHeaderMap() {
        return Collections.unmodifiableMap(headerMap);
    }

    /**
     * @param encoding the encoding to be used 
     * @return the response as a string
     * @throws HttpClientException if anything goes wrong
     */
    public String asString(Charset encoding) throws HttpClientException {

        if (respBytes == null) {
            StringBuilder responseInformation = buildResponseInformation();
            responseInformation.append("No response has been provided by target server.");
            String errorMessage = responseInformation.toString();
            logger.error(errorMessage);
            //throw ExceptionFactory.getException(HttpClientException.class, errorMessage);
            return null;
        }

        return new String(respBytes, encoding);
    }    

    private StringBuilder buildResponseInformation() {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Response status code = ");
        messageBuilder.append(responseCode);
        messageBuilder.append("\n");
        messageBuilder.append("Response status reason = ");
        messageBuilder.append(reasonPhrase);
        messageBuilder.append("\n");
        return messageBuilder;
    }

    public void resolveContent() throws HttpClientException {
        if (respBytes == null) {
            if (responseEntity == null) {
                StringBuilder responseInformation = buildResponseInformation();
                responseInformation.append("No response has been provided by target server.");
                String errorMessage = responseInformation.toString();
                logger.error(errorMessage);
            } else {
                try {
                    respBytes = StreamUtils.getBytes(responseEntity.getContent());
                } catch (IOException e) {
                    logger.error(e, e);
                    throw new HttpClientException(e.getMessage(), e);
                }
            }
        }
    }

    public String getContentType() {
        String ctTyp1 = getHeaderMap().get("Content-Type");
        if (StringUtils.isNotBlank(ctTyp1)) {
            return ctTyp1;
        } else {
            String ctTyp2 = getHeaderMap().get("content-type");
            if (StringUtils.isNotBlank(ctTyp2)) {
                return ctTyp2;
            }
        }
        return null;
    }

    @Override
    public void close() {
        release();
    }
}