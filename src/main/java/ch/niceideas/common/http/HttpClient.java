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

import ch.niceideas.common.utils.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpClient implements Closeable {
    
    private static final Logger logger = Logger.getLogger(HttpClient.class);
    
    private static final int REQUEST_TIMEOUT = 15 * 1000; // 15 seconds
    private static final int MAX_CONNECTIONS = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 5;


    /**
     * FIXME Document me
     */
    public enum HttpMethod {
        POST((uri, content) -> {
            HttpPost httpPost = new HttpPost(uri);
            setEntity(content, httpPost);
            return httpPost;
        }),
        PATCH((uri, content) -> {
            HttpPatch httpPatch = new HttpPatch(uri);
            setEntity(content,httpPatch);
            return httpPatch;
        }),
        PUT((uri, content) -> {
            HttpPut httPut = new HttpPut(uri);
            setEntity(content, httPut);
            return httPut;
        }),
        GET((uri, content) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP GET method");
            }
            return new HttpGet(uri);
        }),
        HEAD((uri, content) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP HEAD method");
            }
            return new HttpHead(uri);
        }),
        DELETE((uri, content) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP DELETE method");
            }
            return new HttpDelete(uri);
        }),
        TRACE((uri, content) -> {
            if (content != null) {
                throw new HttpClientException ("Http content is not supported with the HTTP TRACE method");
            }
            return new HttpTrace(uri);
        });

        HttpMethod(RequestCreator requestCreator) {
            this.requestCreator = requestCreator;
        }

        private final RequestCreator requestCreator;

        public HttpRequest createRequest(String uri, Object content) throws HttpClientException {
            return requestCreator.createRequest(uri.replaceAll("\\|", "%7C"), content);
        }

    }

    private static void setEntity(Object content, HttpEntityEnclosingRequestBase httpEntityOwner) throws HttpClientException {
        try {
            httpEntityOwner.setEntity(buildEntity(content));
        } catch (IOException e) {
            logger.error(e, e);
            throw new HttpClientException (e.getMessage(), e);
        }
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
    private final org.apache.http.client.HttpClient httpClient;

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

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", new SSLConnectionSocketFactory(SSLContexts.createDefault(), (hostname, session) -> {
                    return true; // don't bother
                }))
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(REQUEST_TIMEOUT)
                .setConnectTimeout(REQUEST_TIMEOUT)
                .setSocketTimeout(REQUEST_TIMEOUT)
                .setCircularRedirectsAllowed(true)
                .setCookieSpec(CookieSpecs.DEFAULT)
                .build();

        cm = new PoolingHttpClientConnectionManager(registry);
        cm.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
        cm.setMaxTotal(MAX_CONNECTIONS);
        cm.setValidateAfterInactivity(60 * 1000);

        httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore)
                .setUserAgent(this.userAgent)
                .build();


        String proxyHostParm = System.getProperty("ch.niceideas.common.http.ProxyHost");
        String proxyPortParm = System.getProperty("ch.niceideas.common.http.ProxyPort");
        if (StringUtils.isNotBlank(proxyHostParm)) {
            HttpHost proxyHost;
            if (StringUtils.isNotBlank(proxyPortParm)) {
                proxyHost = new HttpHost(proxyHostParm, Integer.parseInt(proxyPortParm), "http");
            } else {
                proxyHost = new HttpHost(proxyHostParm, 8080, "http");
            }

            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyHost);
        }
        
        /* Uncomment for proxy settings
        
        HttpHost proxyHost = new HttpHost("proxy-internal", 8080, "http");

        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyHost);
        */
    }
    
    /**
     * <b>This is verys important : do not forget to close the httpClient</b>
     */
    public void close() {
        cm.shutdown();
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
        return sendRequest(url, requestParams, null);
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
    public HttpClientResponse sendRequest(String url, Properties requestParams, Object content)
            throws HttpClientException {
        return sendRequest(url, HttpMethod.GET, requestParams, content);
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
    public HttpClientResponse sendRequest(String url, HttpMethod method, Properties requestParams, Object content)
            throws HttpClientException {
        return sendRequest(url, method, requestParams, content, cookieStore);
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
                                          CookieStore cookieStore) throws HttpClientException {

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
        HttpRequest request;
        try {
        	request = method.createRequest(ensureEscaping (uriString + (queryString == null ? "" : queryString)), content);
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
        org.apache.http.HttpResponse response;
        try {
            response = httpClient.execute( //
                    new HttpHost(serverString, StringUtils.isBlank(portString) ? -1 : Integer.parseInt(portString),
                            StringUtils.isBlank(schemeString) ? null : schemeString), //
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

    private HttpClientResponse processResponse(org.apache.http.HttpResponse response, HttpHost targetHost) {
        return new HttpClientResponse(response, targetHost != null ? targetHost.toString() : null);
    }

    private static HttpEntity buildEntity(Object content) throws IOException {
        if (content instanceof InputStream) {
            return new InputStreamEntity((InputStream) content, -1);
        } else if (content instanceof byte[]) {
            return new ByteArrayEntity((byte[]) content);
        } else if (content instanceof String) {
            return new StringEntity((String) content, StandardCharsets.UTF_8);
        }
        // Should remain last case
        else {
            return new StringEntity(content.toString());
        }
    }

    private interface RequestCreator {
        HttpRequest createRequest(String uri, Object content) throws HttpClientException;
    }

}
