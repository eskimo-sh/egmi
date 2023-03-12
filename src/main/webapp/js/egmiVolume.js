/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

 Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
egmi.Volume = function() {

    const that = this;

    this.initialize = function () {

        console.log ("Volume - initialize");

        // Initialize HTML Div from Template
        $("#volume-modal-wrapper").load("html/egmiVolume.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                // clear password on user model everytime it's shown
                const $modalVolume = $("#modal-volume");
                $modalVolume.on('show.bs.modal', () => {
                    $('#volume').val("");
                    $('#button-create-volume').prop('disabled', true);
                });

                // put focus on user email on user edition modal when shown
                $modalVolume.on('shown.bs.modal', () => {
                    $("#volume").focus();
                });

                const $volume = $('#volume');
                $volume.keypress(() => {
                    $('#button-create-volume').prop('disabled', false);
                });
                $volume.change(() => {
                    $('#button-create-volume').prop('disabled', false);
                });

                $("#button-create-volume").click(submitVolume);


            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });

    };

    function showVolumeEdition() {

        // TODO

        $("#modal-volume").modal("show");
    }
    this.showVolumeEdition = showVolumeEdition;

    let submitVolume = function() {

        let newVolume = $('#volume').val();

        if (newVolume && newVolume.trim() !== "") {

            $('#button-create-volume').prop('disabled', true);
            $('#volume-overlay').css('display', 'block');

            //alert (userJSON);

            $.ajaxPost({
                timeout: 1000 * 120,
                url: "volume?volume=" + newVolume,
                success: (data, status, jqXHR) => {

                    // OK
                    //console.log(data);

                    if (!data || data.error) {
                        console.error(data.error);

                        alert(data.error);

                    } else {

                        $('#modal-volume').modal('hide');
                    }

                    $('#button-create-volume').prop('disabled', false);
                    $('#volume-overlay').css('display', 'none');
                },

                error: (jqXHR, status) => {
                    errorHandler(jqXHR, status);
                    $('#button-create-volume').prop('disabled', false);
                    $('#volume-overlay').css('display', 'none');
                }
            });

        }
    };

};
