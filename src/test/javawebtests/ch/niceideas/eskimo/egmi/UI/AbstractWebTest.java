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

package ch.niceideas.eskimo.egmi.UI;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.eskimo.egmi.testinfrastructure.GenerateLCOV;
import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import jscover.Main;
import jscover.report.FileData;
import jscover.report.JSONDataMerger;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class AbstractWebTest {

    private static final Logger logger = Logger.getLogger(AbstractWebTest.class);

    private static final int INCREMENTAL_WAIT_MS = 500;
    private static final int MAX_WAIT_RETRIES = 50;

    private static Thread server;
    private static Main main = null;

    private static final File jsCoverageFlagFile = new File("target/jsCoverageFlag");

    private static final String jsCoverReportDir = "target/jscov-report";
    private static final String[] jsCoverArgs = new String[]{
            "-ws",
            "--document-root=src/main/webapp",
            "--port=9001",
            //"--no-branch",
            //"--no-function",
            //"--no-instrument=example/lib",
            "--log=INFO",
            "--report-dir=" + jsCoverReportDir
    };

    private static String className = null;
    private static final List<String> coverages = new ArrayList<>();

    private static final JSONDataMerger jsonDataMerger = new JSONDataMerger();

    protected WebClient webClient;
    protected HtmlPage page;

    Object js (String jsCode) {
        return page.executeJavaScript (jsCode).getJavaScriptResult();
    }

    @BeforeAll
    public static void setUpOnce() {
        if (isCoverageRun()) {
            main = new Main();
            server = new Thread(() -> main.runMain(jsCoverArgs));
            server.start();
        }
    }

    @AfterAll
    public static void tearDownOnce() throws Exception {
        if (isCoverageRun()) {
            main.stop();

            File targetFile = new File(jsCoverReportDir + "/" + className, "jscoverage.json");
            targetFile.getParentFile().mkdirs();
            FileUtils.write(targetFile, mergeJSON());
        }
    }

    @BeforeEach
    public void setUpClassName() {
        Class<?> clazz = this.getClass(); //if you want to get Class object
        className = clazz.getCanonicalName(); //you want to get only class name
    }

    @AfterEach
    public void tearDown() {
        if (isCoverageRun()) {
            js("window.jscoverFinished = false;");
            js("jscoverage_report('', function(){window.jscoverFinished=true;});");

            await().atMost(MAX_WAIT_RETRIES * 500 * (isCoverageRun() ? 2 : 1)  , TimeUnit.SECONDS).until(
                    () -> (Boolean) js("window.jscoverFinished"));

            // FIXME remove after I ensure awaitility is fixed
            /*
            int attempt = 0;
            while ((!(Boolean) (js("window.jscoverFinished"))) && attempt < 10) {
                logger.debug("Waiting for coverage report to be written ...");
                //noinspection BusyWait
                Thread.sleep(500);
                attempt++;
            }
            */

            String json = (String) (js("jscoverage_serializeCoverageToJSON();"));
            coverages.add(json);
        }
    }

    private static String mergeJSON() {
        SortedMap<String, FileData> total = new TreeMap<>();
        for (String json : coverages) {
            total = jsonDataMerger.mergeJSONCoverageMaps(total, jsonDataMerger.jsonToMap(json));
        }
        return GenerateLCOV.toJSON(total);
    }

    protected static boolean isCoverageRun() {
        //return true;
        return jsCoverageFlagFile.exists();
    }

    protected final void loadScript (String script) {
        if (isCoverageRun()) {
            js("loadScript('http://localhost:9001/js/"+script+"')");
        } else {
            js("loadScript('../../src/main/webapp/js/"+script+"')");
        }
    }

    @BeforeEach
    public void init() throws Exception {
        init("classpath:GenericTestPage.html");
    }

    protected void init(String target, String ... arguments) throws Exception {
        webClient = new WebClient();

        webClient.setAlertHandler((page, s) -> logger.info(s));

        webClient.setAjaxController(new AjaxController() {
            // TODO
        });

        URL testPage = new URL (ResourceUtils.getURL(target).toExternalForm() + "?" + String.join("&", arguments));
        page = webClient.getPage(testPage);
        Assert.assertEquals("Generic Test Page", page.getTitleText());

        // 3 attempts
        for (int i = 0; i < 3 ; i++) {
            logger.info ("Loading jquery : attempt " + i);
            loadScript("vendor/jquery-3.6.0.js");

            waitForDefinition("window.$");

            if (!js("typeof window.$").toString().equals ("undefined")) {
                break;
            }
        }

        waitForDefinition("$.fn");

        // override jquery load
        js("$.fn._internalLoad = $.fn.load;");
        js("$.fn.load = function (resource, callback) { return this._internalLoad ('../../src/main/webapp/'+resource, callback); };");

    }

    @AfterEach
    public void close() {
        webClient.close();
    }

    protected void assertAttrValue(String selector, String attribute, String value) {
        assertEquals (value, js("$('"+selector+"').attr('"+attribute+"')"));
    }

    protected void assertCssValue(String selector, String attribute, String value) {
        assertEquals (value, js("$('"+selector+"').css('"+attribute+"')"));
    }

    protected void assertJavascriptEquals(String value, String javascript) {
        assertEquals (value, js(javascript).toString());
    }

    protected void assertJavascriptNull(String javascript) {
        assertNull (js(javascript));
    }

    protected void assertTagName(String elementId, String tagName) {
        assertEquals (tagName, page.getElementById(elementId).getTagName());
    }

    protected void waitForElementIdInDOM(String elementId) {

        await().atMost(MAX_WAIT_RETRIES * INCREMENTAL_WAIT_MS * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> page.getElementById(elementId) != null);


        // FIXME remove after I ensure awaitility is fixed
        /*
        int attempt = 0;
        while (page.getElementById(elementId) == null && attempt < MAX_WAIT_RETRIES) {
            //noinspection BusyWait
            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
        */
    }

    protected void waitForDefinition(String varName) {

        await().atMost(INCREMENTAL_WAIT_MS * MAX_WAIT_RETRIES * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> !js("typeof " + varName).toString().equals ("undefined"));

        // FIXME remove after I ensure awaitility is fixed
        /*
        int attempt = 0;
        while (js("typeof " + varName).toString().equals ("undefined") && attempt < MAX_WAIT_RETRIES) {
            //noinspection BusyWait
            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
        */
    }
}
