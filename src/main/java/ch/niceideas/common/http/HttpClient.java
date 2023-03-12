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

import ch.niceideas.common.utils.StringUtils;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpClient implements Closeable {
    
    private static final Logger logger = Logger.getLogger(HttpClient.class);
    
    private static final int REQUEST_TIMEOUT = 20 * 1000; // 20 seconds
    private static final int MAX_CONNECTIONS = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 5;


    /**
     * FIXME Document me
     */
    public enum HttpMethod {
        POST((uri, content, type) -> {
            HttpPost httpPost = new HttpPost(uri);
            if (content != null) {
                setEntity(content, httpPost,
                        Optional.ofNullable(type)
                                .orElseThrow(() -> new HttpClientException ("A content type is required when passing an HTTP entity")));
            }
            return httpPost;
        }),
        PATCH((uri, content, type) -> {
            HttpPatch httpPatch = new HttpPatch(uri);
            if (content != null) {
                setEntity(content, httpPatch,
                        Optional.ofNullable(type)
                                .orElseThrow(() -> new HttpClientException ("A content type is required when passing an HTTP entity")));
            }
            return httpPatch;
        }),
        PUT((uri, content, type) -> {
            HttpPut httPut = new HttpPut(uri);
            if (content != null) {
                setEntity(content, httPut,
                        Optional.ofNullable(type)
                                .orElseThrow(() -> new HttpClientException ("A content type is required when passing an HTTP entity")));
            }
            return httPut;
        }),
        GET((uri, content, type) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP GET method");
            }
            return new HttpGet(uri);
        }),
        HEAD((uri, content, type) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP HEAD method");
            }
            return new HttpHead(uri);
        }),
        DELETE((uri, content, type) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP DELETE method");
            }
            return new HttpDelete(uri);
        }),
        TRACE((uri, content, type) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP TRACE method");
            }
            return new HttpTrace(uri);
        });

        HttpMethod(RequestCreator requestCreator) {
            this.requestCreator = requestCreator;
        }

        private final RequestCreator requestCreator;

        public BasicClassicHttpRequest createRequest(String uri, Object content, ContentType type) throws HttpClientException {
            return requestCreator.createRequest(uri.replaceAll("\\|", "%7C"), content, type);
        }

    }

    private static void setEntity(Object content, ClassicHttpRequest httpEntityOwner, ContentType type) {
        httpEntityOwner.setEntity(buildEntity(content, type));
    }

    /** Simulate IE 7.0 behaviour */
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36";

    private String userAgent;
    private final Map<String, String> defaultHeaders;
    private final CookieStore cookieStore = new BasicCookieStore();

    /** Request validation */
    public static final Pattern requestValidationPattern = Pattern.compile("" +
            "((http|https)://)?" +
            "([\\p{L}\\p{M}*_0-9.\\-]+)" +
            "(:((\\d)+))?" +
            "((/[\\p{L}\\p{M}_0-9.,@\\-+:;|%=*’()\\[\\]${}~&]*)*)" +
            "(([?#])[\\p{L}\\p{M}_0-9.\\-:;?#|&= ’+/,'\"%!@*()\\[\\]$<>{}~]*)*");

    private final PoolingHttpClientConnectionManager cm;
    private final CloseableHttpClient httpClient;

    /**
     * Default constructor
     */
    public HttpClient() {
        this (DEFAULT_USER_AGENT);
    }

    public HttpClient(String userAgent) {
        this (userAgent, null);
    }

    public HttpClient(String userAgent, Map<String, String> defaultHeaders) {

        if (StringUtils.isNotBlank(userAgent)) {
            this.userAgent = userAgent;
        }

        this.defaultHeaders = defaultHeaders;

        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setCookieSpec(StandardCookieSpec.RELAXED)
                .setResponseTimeout(Timeout.defaultsToDisabled(Timeout.ofMilliseconds(REQUEST_TIMEOUT)))
                .setCircularRedirectsAllowed(true)
                .setConnectionRequestTimeout(Timeout.defaultsToDisabled(Timeout.ofMilliseconds(REQUEST_TIMEOUT)))
                .build();

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT))
                .build();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.defaultsToDisabled(Timeout.ofMilliseconds(REQUEST_TIMEOUT)))
                .setSocketTimeout(Timeout.defaultsToDisabled(Timeout.ofMilliseconds(REQUEST_TIMEOUT)))
                .build();

        cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(SSLContexts.createDefault(), (hostname, session) -> {
                    return true; // don't bother
                }))
                .setDefaultSocketConfig(socketConfig)
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore)
                .setUserAgent(this.userAgent)
                .build();

        
        /* Uncomment for proxy settings
        
        HttpHost proxyHost = new HttpHost("proxy-internal", 8080, "http");

        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyHost);
        */
    }
    
    /**
     * <b>This is verys important : do not forget to close the httpClient</b>
     */
    public void close() {
        cm.close();
    }

    /**
     * Send an HTTP request providing as argument the target URL.
     * <p />
     * 
     * The HTTP method is implied and is POST. No content and no parameter is bound to the outgoing HTTP request.
     * 
     * @param url The target URL. Expected of the form <code>[http://]serverAddress:serverPort[/path/to/page]</code>
     *            . No URL parameter is accepted.
     * @return the HTTP response converted to the desired class.
     * @throws HttpClientException in case anything wrong occurd
     */
    public HttpClientResponse sendRequest(String url) throws HttpClientException {
        return sendRequest(url, null);
    }

    /**
     * Send an HTTP request providing as argument the target URL and the request parameters.
     * <p />
     * 
     * The HTTP method is implied and is POST. No content is bound to the outgoing HTTP request.
     * 
     * @param url The target URL. Expected of the form <code>[http://]serverAddress:serverPort[/path/to/page]</code>
     *            . No URL parameter is accepted.
     * @param requestParams the request parameters such as the content of a submited form or the URL parameters. The
     *            given properties can be null or empty
     * @return the HTTP response converted to the desired class.
     * @throws HttpClientException in case anything wrong occurd
     */
    public HttpClientResponse sendRequest(String url, Properties requestParams) throws HttpClientException {
        return sendRequest(url, requestParams, null, null);
    }

    /**
     * Send an HTTP request providing as argument the target URL, the request parameters and the content of the
     * request.
     * <p />
     * 
     * The HTTP method is implied and is POST.
     * 
     * @param url The target URL. Expected of the form <code>[http://]serverAddress:serverPort[/path/to/page]</code>
     *            . No URL parameter is accepted.
     * @param requestParams the request parameters such as the content of a submited form or the URL parameters. The
     *            given properties can be null or empty
     * @param content the content of the request. Only accepted for the POST HTTP method. The content can be null.
     * @return the HTTP response converted to the desired class.
     * @throws HttpClientException in case anything wrong occurd
     */
    public HttpClientResponse sendRequest(String url, Properties requestParams, Object content, ContentType type)
            throws HttpClientException {
        return sendRequest(url, HttpMethod.GET, requestParams, content, type);
    }

    /**
     * Send an HTTP request providing as argument the target URL, the HTTP method to be used, the request parameters
     * and the content of the request.
     * 
     * @param url The target URL. Expected of the form <code>[http://]serverAddress:serverPort[/path/to/page]</code>
     *            . No URL parameter is accepted.
     * @param method the HTTP method to be used. Supported methods are : POST, PUT, GET, HEAD, DELETE, TRACE
     * @param requestParams the request parameters such as the content of a submited form or the URL parameters. The
     *            given properties can be null or empty
     * @param content the content of the request. Only accepted for the POST HTTP method. The content can be null.
     * @return the HTTP response
     * @throws HttpClientException in case anything wrong occurd
     */
    public HttpClientResponse sendRequest(String url, HttpMethod method, Properties requestParams, Object content, ContentType type)
            throws HttpClientException {
        return sendRequest(url, method, requestParams, content, type, cookieStore);
    }

    /**
     * Send an HTTP request providing as argument the target URL, the HTTP method to be used, the request parameters
     * and the content of the request.
     * 
     * @param url The target URL. Expected of the form <code>[http://]serverAddress:serverPort[/path/to/page]</code>
     *            . No URL parameter is accepted.
     * @param method the HTTP method to be used. Supported methods are : POST, PUT, GET, HEAD, DELETE, TRACE
     * @param requestParams the request parameters such as the content of a submited form or the URL parameters. The
     *            given properties can be null or empty
     * @param content the content of the request. Only accepted for the POST HTTP method. The content can be null.
     * @param cookieStore a CookieStore to be used to store cookies between different requests
     * @return the HTTP response
     * @throws HttpClientException in case anything wrong occurd
     */
    public HttpClientResponse sendRequest(String url, HttpMethod method, Properties requestParams, Object content,
                                          ContentType type, CookieStore cookieStore) throws HttpClientException {

        // 1. ValidateRequest
        Matcher matcher = requestValidationPattern.matcher(url);
        if (!matcher.matches()) {
            logger.error("Unknown URL form : " + url);
            throw new HttpClientException ("Unknown URL form : " + url);
        }

        // 2. Extract Server name, server port, scheme and
        String schemeString = matcher.group(2);
        String serverString = matcher.group(3);
        String portString = matcher.group(5);
        String uriString = matcher.group(7);
        String queryString = matcher.group(9);



        // 3. Create Http request
        BasicClassicHttpRequest request;
        try {
        	request = method.createRequest(ensureEscaping (uriString + (queryString == null ? "" : queryString)), content, type);
        } catch (IllegalArgumentException e) {
        	logger.warn (e, e);
        	throw new HttpClientException (e.getMessage(), e);
        }

        // 4. Set request parameters
        if (this.defaultHeaders != null) {
            this.defaultHeaders.keySet().forEach(
                    key -> request.setHeader(key, defaultHeaders.get(key)));
        }

        if (requestParams != null) {
            requestParams.keySet().forEach(
                    key -> request.setHeader(key.toString(), requestParams.getProperty(key.toString()))
            );
        }

        HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

        // 5. Send request
        ClassicHttpResponse response;
        try {
            response = httpClient.execute( //
                    new HttpHost(StringUtils.isBlank(schemeString) ? "http" : schemeString,
                            serverString,
                            StringUtils.isBlank(portString) ? -1 : Integer.parseInt(portString)), //
                    request,
                    context);

        } catch (NumberFormatException | IOException e) {
            logger.debug(e, e);

            throw new HttpClientException (e.getMessage(), e);
        }
        
        // 6. Process response.
        return processResponse(response, (HttpHost) context.getAttribute("http.target_host"));
    }

    public static String ensureEscaping(String s) {
        return s.trim()
                // generic cases
                .replace(" ", "%20")
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace("\"", "%22")
                .replace("<", "%3C")
                .replace(">", "%3E")
                // specific cases (hacks ...)
                .replace("%%", "%25%25")
                .replace("%eot", "%25eot")
                .replace("%woff", "%25woff")
                .replace("%woff2", "%25woff2")
                .replace("%ttf", "%25ttf")
                .replace("%svg", "%25svg")
                ;
    }

    private HttpClientResponse processResponse(ClassicHttpResponse response, HttpHost targetHost) {
        return new HttpClientResponse(response, targetHost != null ? targetHost.toString() : null);
    }

    private static HttpEntity buildEntity(Object content, ContentType type) {
        if (content instanceof InputStream) {
            return new InputStreamEntity((InputStream) content, type);
        } else if (content instanceof byte[]) {
            return new ByteArrayEntity((byte[]) content, type);
        } else if (content instanceof String) {
            return new StringEntity((String) content, StandardCharsets.UTF_8);
        }
        // Should remain last case
        else {
            return new StringEntity(content.toString());
        }
    }

    private interface RequestCreator {
        BasicClassicHttpRequest createRequest(String uri, Object content, ContentType type) throws HttpClientException;
    }

}
