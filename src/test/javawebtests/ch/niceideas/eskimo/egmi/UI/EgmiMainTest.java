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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EgmiMainTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("vendor/bootstrap.js");
        loadScript("vendor/bootstrap-select.js");
        loadScript("utils.js");

        loadScript("egmiMain.js");

        js("egmi.Messaging = function() {" +
                "this.initialize = function() {}" +
                "};");
        js("egmi.Node = function() {" +
                "this.initialize = function() {}" +
                "};");
        js("egmi.Volume = function() {" +
                "this.initialize = function() {}" +
                "};");
        js("egmi.Action = function() {" +
                "this.initialize = function() {};" +
                "this.showActionConfirm = function(message, callback) {\n" +
                "   callback(function() {});\n" +
                "};" +
                "};");

        js("main = new egmi.Main();");

        waitForElementIdInDOM("clear-messages");
    }

    @Test
    public void testRenderVolumeStatus() throws Exception {
        String volumesInfo = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EgmiMainTest/volumesInfo.json"));
        js("main.renderVolumeStatus(" + volumesInfo + ")");

        assertJavascriptEquals("9.0", "$('#status-volume-table * tr').length");

        assertJavascriptEquals("63.0", "$('#status-volume-table-body tr td').length");

        assertJavascriptEquals("OK", "$('#status-volume-table-body tr td:eq(2)').html()");
        assertJavascriptEquals("3 / 3", "$('#status-volume-table-body tr td:eq(5)').html()");
        assertJavascriptEquals("/dev/sda1", "$('#status-volume-table-body tr td:eq(10)').html()");
        assertJavascriptEquals("40.0GB", "$('#status-volume-table-body tr td:eq(20)').html()");
    }

    @Test
    public void testRenderNodesStatus() throws Exception {
        String nodesInfo = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EgmiMainTest/nodesInfo.json"));
        js("main.renderNodeStatus(" + nodesInfo + ")");

        assertJavascriptEquals("5.0", "$('#status-node-table * tr').length");

        assertJavascriptEquals("16.0", "$('#status-node-table-body tr td').length");

        assertJavascriptEquals("test1, test2", "$('#status-node-table-body tr td:eq(2)').html()");
        assertJavascriptEquals("OK", "$('#status-node-table-body tr td:eq(5)').html()");
        assertJavascriptEquals("test1, test2", "$('#status-node-table-body tr td:eq(10)').html()");
        assertJavascriptEquals("1", "$('#status-node-table-body tr td:eq(15)').html()");
    }

    @Test
    public void testVolumeAction() throws Exception {
        testRenderVolumeStatus();

        js("$.ajaxGet = function(object) {" +
                "  window.url = object.url;" +
                "  object.success ( {\"status\" : \"OK\"} )" +
                "}");

        page.getElementById("stop_test2").click();

        assertJavascriptEquals("stop-volume?volume=test2", "window.url");
    }
}

