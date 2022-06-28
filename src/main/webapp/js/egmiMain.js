/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

 Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software,
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/

if (typeof egmi === "undefined" || egmi == null) {
    window.egmi = {}
}

egmi.Main = function() {

    const STATUS_UPDATE_INTERVAL = 5000;
    const QUERY_MASTER_INTERVAL = 10000;

    const that = this;

    let messaging = null;
    let node = null;
    let volume = null;
    let action = null;
    let showOptions = null;

    let statusUpdateTimeoutHandler = null;
    let masterQueryTimeoutHandler = null;

    this.initialize = function() {

        console.log ("Main - initialize");

        $("#wrapper").load("html/egmiMain.html", function (responseTxt, statusTxt, jqXHR) {

            messaging = new egmi.Messaging({
                egmiMain: this,
            });
            messaging.initialize();

            node = new egmi.Node({
                egmiMain: this
            });
            node.initialize();

            volume = new egmi.Volume({
                egmiMain: this
            });
            volume.initialize();

            action = new egmi.Action({
                egmiMain: this
            });
            action.initialize();

            showOptions = new egmi.ShowOptions({
                egmiMain: this
            });
            showOptions.initialize();

            updateStatus();

            $("#add-node-btn").click(node.showNodeEdition);

            $("#add-volume-btn").click(volume.showVolumeEdition);

            queryMaster();

        });
    };

    this.initialize();

    function renderEmptyNodes () {
        $("#status-node-table-body").html("" +
            "<tr id=\"empty-nodes\">\n" +
            "    <td class=\"status-node-cell\" colspan=\"4\">\n" +
            "        (No Data to display)\n" +
            "    </td>\n" +
            "</tr>")
    }

    function renderEmptyVolumes () {
        $("#status-volume-table-body").html("" +
            "<tr id=\"empty-nodes\">\n" +
            "    <td class=\"status-node-cell\" colspan=\"14\">\n" +
            "        (No Data to display)\n" +
            "    </td>\n" +
            "</tr>")
    }

    let feedInTableCell = function (value, leftFlag) {
        return "<td class=\"status-node-cell " + (leftFlag ? "status-node-cell-left" : "") + " \">"
                + value
                + "</td>";
    }

    let renderBrick = function (volRow, brick) {

        volRow += feedInTableCell(brick.number && brick.number !== "" ? brick.number : "-");

        volRow += feedInTableCell(brick.node && brick.node !== "" ? brick.node : "-");

        volRow += feedInTableCell(brick.path && brick.path !== "" ? brick.path : "-", true);

        volRow += feedInTableCell(brick.status && brick.status !== "" ? brick.status : "-");

        volRow += feedInTableCell(brick.device && brick.device !== "" ? brick.device : "-");

        volRow += feedInTableCell(brick.free && brick.free !== "" ? brick.free : "-");

        volRow += feedInTableCell(brick.tot && brick.tot !== "" ? brick.tot : "-");

        return volRow;
    };

    let feedInTableCellRowSpan = function (value, brickCount) {
        return "<td class=\"status-node-cell\" "+ (brickCount > 0 ? "rowspan=\"" + brickCount + "\"" : "") + ">"
            + value
            + "</td>";
    }

    let renderVolumeStatus = function (volumesData) {

        $("#options-holder").html("");

        if (volumesData == null || volumesData.length <= 0) {
            renderEmptyVolumes();
        } else {
            $("#status-volume-table-body").html("");

            for (let i = 0; i < volumesData.length; i++) {

                let volume = volumesData[i];

                // render options holder
                let volumeOptions = volume.options;
                if (volumeOptions && !jQuery.isEmptyObject(volumeOptions)) {
                    let volumeOptionsHolder = '<div id="volume-options-' + volume.volume + '"><pre>';
                    for (let optionKey in volumeOptions) {
                        let optionValue = volumeOptions[optionKey];
                        volumeOptionsHolder += (optionKey.replace("__", ".") + '=' + optionValue + '\n');
                    }
                    volumeOptionsHolder += "</pre></div>";
                    $("#options-holder").append($(volumeOptionsHolder));
                }

                let brickCount = volume.bricks.length;

                let volRow = "<tr>";

                volRow += feedInTableCellRowSpan(volume.volume && volume.volume !== "" ? volume.volume : "-", brickCount);

                volRow += feedInTableCellRowSpan(volume.type && volume.type !== "" ? volume.type : "-", brickCount);

                volRow += feedInTableCellRowSpan(volume.status && volume.status !== "" ? volume.status : "-", brickCount);

                volRow += feedInTableCellRowSpan(volume.nb_shards && volume.nb_shards !== "" ? volume.nb_shards : "-", brickCount);

                volRow += feedInTableCellRowSpan(volume.nb_replicas && volume.nb_replicas !== "" ? volume.nb_replicas : "-", brickCount);

                volRow += feedInTableCellRowSpan(volume.nb_bricks && volume.nb_bricks !== "" ? volume.nb_bricks : "-", brickCount);

                if (brickCount > 0) {
                    let brick = volume.bricks[0];
                    volRow = renderBrick(volRow, brick);
                } else {

                    volRow += "<td class=\"status-node-cell\" colspan=\"7\">"
                        + "<span style=\"font-style: italic;\">(No brick found)</span>"
                        + "</td>";
                }

                if (volume.status && volume.status !== "" && volume.status !== "NO VOLUME") {
                    volRow += "<td class=\"status-node-cell\" style=\"min-width: 130px; width: 130px;\"" + (brickCount > 0 ? "rowspan=\"" + brickCount + "\"" : "") + ">"
                        + "<button type=\"button\" id=\"start_" + volume.volume + "\" title=\"Start Volume\" class=\"btn btn-light btn-action\"><span class=\"fa fa-play\"></span></button>&nbsp;"
                        + "<button type=\"button\" id=\"stop_" + volume.volume + "\" title=\"Stop Volume\" class=\"btn btn-light btn-action\"><span class=\"fa fa-pause\"></span></button>&nbsp;"
                        + "<button type=\"button\" id=\"remove_" + volume.volume + "\" title=\"Delete Volume\" class=\"btn btn-light btn-action\"><span class=\"fa fa-remove\"></span></button>&nbsp;"
                        + "<button type=\"button\" id=\"show_options_" + volume.volume + "\" title=\"Show Options\" class=\"btn btn-light btn-action\"><span class=\"fa fa-info\"></span></button>"
                        // &nbsp;&nbsp;
                        //+ "<a href=\"#\" id=\"add_" + volume.volume + "\" title=\"Add Brick\"><span class=\"fa fa-plus\"></span></a>&nbsp;"
                        + "</td>";
                } else {
                    volRow += "<td class=\"status-node-cell\" style=\"min-width: 70px;\"" + (brickCount > 0 ? "rowspan=\"" + brickCount + "\"" : "") + ">"
                        + "</td>";
                }

                volRow += "</tr>";

                $("#status-volume-table-body").append($(volRow));

                for (let j = 1; j < brickCount; j++) {

                    let brickRow = "<tr>";

                    let brick = volume.bricks[j];
                    brickRow = renderBrick(brickRow, brick);

                    brickRow += "</tr>";

                    $("#status-volume-table-body").append($(brickRow));
                }

                $("#start_" + volume.volume).click(function() {
                    volumeAction (volume.volume, "start");
                });

                $("#stop_" + volume.volume).click(function() {
                    volumeAction (volume.volume, "stop");
                });

                $("#remove_" + volume.volume).click(function() {
                    volumeAction (volume.volume, "remove");
                });

                $("#show_options_" + volume.volume).click(function() {
                    showOptions.showOptions (volume.volume);
                });
            }
        }
    };
    this.renderVolumeStatus = renderVolumeStatus;

    let volumeAction = function (volume, actionType) {
        action.showActionConfirm(
            "Are you sure you want to " + actionType + " volume " + volume + " ?",
            function(completer) {
                $.ajaxGet({
                    timeout: 1000 * 120,
                    url: actionType + "-volume?volume=" + volume,
                    success: function (data, status, jqXHR) {

                        // OK
                        //console.log(data);

                        if (!data || data.error) {
                            console.error(data.error);

                            alert(data.error);
                        } else {
                            alert("Volume " + volume + " " + actionType + " command executed successfully");
                        }

                        completer();
                    },

                    error: function (jqXHR, status) {
                        errorHandler(jqXHR, status);
                        completer();
                    }
                });
            }
        );
    };

    let renderNodeStatus = function (nodesData) {

        if (nodesData == null || nodesData.length <= 0) {
            renderEmptyNodes();
        } else {
            $("#status-node-table-body").html("");

            // TODO

            for (let i = 0; i < nodesData.length; i++) {

                let node = nodesData[i];

                let nodeRow = "<tr>";

                nodeRow += "<td class=\"status-node-cell\">"
                    + (node.host && node.host !== "" ? node.host : "-")
                    + "</td>";

                nodeRow += "<td class=\"status-node-cell\">"
                    + (node.status && node.status !== "" ? node.status : "-")
                    + "</td>";

                nodeRow += "<td class=\"status-node-cell status-node-cell-left\">"
                    + (node.volumes && node.volumes !== "" ? node.volumes : "-")
                    + "</td>";

                nodeRow += "<td class=\"status-node-cell\">"
                    + (node.nbr_bricks && node.nbr_bricks !== "" ? node.nbr_bricks : "-")
                    + "</td>";

                nodeRow += "</tr>";

                $("#status-node-table-body").append($(nodeRow));

            }
        }
    };
    this.renderNodeStatus = renderNodeStatus;

    let inUpdateStatus = false;
    let updateStatus = function () {

        if (inUpdateStatus) {
            return;
        }
        inUpdateStatus = true;

        // cancel previous timer. update status will be rescheduled at the end of this method
        if (statusUpdateTimeoutHandler != null) {
            clearTimeout(statusUpdateTimeoutHandler);
        }


        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-status",
            success: function (data, status, jqXHR) {

                if (!data.clear) {

                    $("#node-indicator").html("on " + data.hostname);

                    renderVolumeStatus (data.volumes);

                    renderNodeStatus (data.nodes);

                } else if (data.clear == "init" || data.clear == "initialzing"){

                    renderEmptyNodes ();
                    renderEmptyVolumes();
                }

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            }
        });
    };
    this.updateStatus = updateStatus;

    let queryMaster = function () {

        // cancel previous timer. update status will be rescheduled at the end of this method
        if (masterQueryTimeoutHandler != null) {
            clearTimeout(masterQueryTimeoutHandler);
        }


        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-master",
            success: function (data, status, jqXHR) {

                if (!data.clear) {

                    if (!data.master) {

                        let masterURL = data.master_url;
                        if (masterURL && (""+masterURL).trim() !== "") {

                            $('#master-problem').css ("display", "none");

                            if ( ("" + window.location.href) !== masterURL) {
                                window.location = masterURL;
                            }

                        } else {
                            $('#master-problem').css ("display", "block");
                        }
                    } else {
                        $('#master-problem').css ("display", "none");
                    }

                } else {
                    $('#master-problem').css ("display", "block");
                }

                // reschedule updateStatus
                masterQueryTimeoutHandler = setTimeout(queryMaster, QUERY_MASTER_INTERVAL);
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);

                $('#master-problem').css ("display", "block");

                // reschedule updateStatus
                masterQueryTimeoutHandler = setTimeout(queryMaster, QUERY_MASTER_INTERVAL);
            }
        });
    };
    this.queryMaster = queryMaster;
};