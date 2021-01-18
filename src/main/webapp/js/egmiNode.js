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
egmi.Node = function() {

    const that = this;

    this.initialize = function () {

        console.log ("Node - initialize");

        // Initialize HTML Div from Template
        $("#node-modal-wrapper").load("html/egmiNode.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt === "success") {

                // clear password on user model everytime it's shown
                $("#modal-node").on('show.bs.modal', function(){
                    $('#node').val("");
                    $('#button-create-node').prop('disabled', true);
                });

                // put focus on user email on user edition modal when shown
                $("#modal-node").on('shown.bs.modal', function(){
                    $("#node").focus();
                });

                $('#node').keypress(function() {
                    $('#button-create-node').prop('disabled', false);
                });

                $('#node').change(function() {
                    $('#button-create-node').prop('disabled', false);
                });

                $("#button-create-node").click(submitNode);


            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });

    };

    let showNodeEdition = function() {

        $('#node').val("");

        $("#modal-node").modal();
    };
    this.showNodeEdition = showNodeEdition;

    let submitNode = function() {

        let newNode = $('#node').val();

        if (newNode && !newNode.trim() == "") {

            $('#button-create-node').prop('disabled', true);
            $('#node-overlay').css('display', 'block');

            //alert (userJSON);

            $.ajaxPost({
                timeout: 1000 * 120,
                url: "node?node=" + newNode,
                success: function (data, status, jqXHR) {

                    // OK
                    //console.log(data);

                    if (!data || data.error) {
                        console.error(data.error);

                        alert(data.error);

                    } else {

                        $('#modal-node').modal('hide');
                    }

                    $('#button-create-node').prop('disabled', false);
                    $('#node-overlay').css('display', 'none');
                },

                error: function (jqXHR, status) {
                    errorHandler(jqXHR, status);
                    $('#button-create-node').prop('disabled', false);
                    $('#node-overlay').css('display', 'none');
                }
            });

        }
    };

};
