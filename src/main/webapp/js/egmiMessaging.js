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
egmi.Messaging = function(constrObj) {

    this.egmiMain = constrObj.egmiMain;

    const MAX_MESSAGES_COUNT = 20000;

    const MESSAGING_POLLING_DELAY = 5000;

    const that = this;

    var messagingPollingHandle = null;

    var lastLineMessaging = 0;
    var messagesCounter = 0;

    this.initialize = function () {
        loadLastLine();

        $("#clear-messages").click(function(e) {
            $("#pending-message-content").html("");

            e.preventDefault();
            return false;
        });

        $("#pending-message-content").html("");

        messagingPollingHandle = setTimeout(
            fetchLastMessages,
            MESSAGING_POLLING_DELAY);
    };

    // get last line of messaging
    function loadLastLine() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-lastline-messaging",
            success: function (data, status, jqXHR) {
                if (data && data.status) {
                    lastLineMessaging = data.lastLine;
                } else {
                    console.error(data);
                }
            },
            error: errorHandler
        });
    }


    function fetchLastMessages(callback) {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "fetch-messaging?last_line="+lastLineMessaging,
            success: function (data, status, jqXHR) {

                // OK
                //console.log(data);

                if (data && data.status) {
                    //console.log (atob(data.lines));

                    addMessage(atob(data.lines));

                    lastLineMessaging = data.lastLine;

                } else {
                    console.error("No data received");
                }

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                if (!callback) {
                    messagingPollingHandle = setTimeout(
                        fetchLastMessages,
                        MESSAGING_POLLING_DELAY);
                }
            },
            error: function (jqXHR, status) {

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                errorHandler(jqXHR, status);

                if (!callback) {
                    messagingPollingHandle = setTimeout(
                        fetchLastMessages,
                        MESSAGING_POLLING_DELAY);
                }
            }
        });
    }
    this.fetchLastMessages = fetchLastMessages;

    function addMessage (message) {
        messagesCounter++;
        // safety net
        if (messagesCounter > MAX_MESSAGES_COUNT) {
            $("#pending-message-content").html("");
        }
        $("#pending-message-content").append(message);
    }
    this.addMessage = addMessage;


};