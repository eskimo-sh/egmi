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


function noOp() {

}

$.fn.serializeObject = function() {
    let o = {};
    let a = this.serializeArray();
    $.each(a, function() {
        if (o[this.name]) {
            if (!o[this.name].push) {
                o[this.name] = [o[this.name]];
            }
            o[this.name].push(this.value || '');
        } else {
            o[this.name] = this.value || '';
        }
    });
    return o;
};

$.ajaxDelete = function (reqObject) {
    $._ajaxSendContent ("DELETE", reqObject);
};

$.ajaxPost = function (reqObject) {
    $._ajaxSendContent ("POST", reqObject);
};

$.ajaxPut = function (reqObject) {
    $._ajaxSendContent ("PUT", reqObject);
};

let defaultSuccess = function (data, status, jqXHR) {
    if (!data || data.error) {
        console.error(data.error);
        alert(data.error);
    }
};

$._ajaxSendContent = function(verb, reqObject) {

    let success = defaultSuccess;
    if (typeof reqObject.success !== 'undefined') {
        success = reqObject.success;
    }

    let error = errorHandler;
    if (typeof reqObject.error !== 'undefined') {
        error = reqObject.error;
    }

    $.ajax({
        type: verb,
        dataType: (typeof reqObject.dataType === 'undefined' ? "json" : reqObject.dataType),
        contentType: (typeof reqObject.contentType === 'undefined' ? "application/json; charset=utf-8" : reqObject.contentType),
        timeout: (typeof reqObject.timeout === 'undefined' ? 1000 * 20 : reqObject.timeout),
        url: reqObject.url,
        data: reqObject.data,
        success: success,
        error: error
    });
};

$.ajaxGet = function(reqObject) {

    let success = defaultSuccess;
    if (typeof reqObject.success !== 'undefined') {
        success = reqObject.success;
    }

    let error = errorHandler;
    if (typeof reqObject.error !== 'undefined') {
        error = reqObject.error;
    }

    $.ajax({
        type: "GET",
        dataType: (typeof reqObject.dataType === 'undefined' ? "json" : reqObject.dataType),
        contentType: (typeof reqObject.contentType === 'undefined' ? "application/json; charset=utf-8" : reqObject.contentType),
        timeout: (typeof reqObject.timeout === 'undefined' ? 1000 * 10 : reqObject.timeout),
        url: reqObject.url,
        success: success,
        error: error
    });
};

function errorHandler (jqXHR, status) {
    // error handler
    console.log (jqXHR);
    console.log (status);

    if (jqXHR.status == "401") {
        window.location = "app.html";
    }

    if (jqXHR && jqXHR.responseJSON  && jqXHR.responseJSON.message) {
        console.error('fail : ' + jqXHR.responseJSON.message);
    } else if (jqXHR && jqXHR.responseJSON  && jqXHR.responseJSON.error) {
        console.error('fail : ' + jqXHR.responseJSON.error);
    } else {
        console.error('fail : ' + status);
    }
}

function isFunction(functionToCheck) {
    if (!functionToCheck) {
        return false;
    }
    return {}.toString.call(functionToCheck) === '[object Function]';
}

function swapElements(elm1, elm2) {
    let parent1, next1,
        parent2, next2;

    parent1 = elm1.parentNode;
    next1   = elm1.nextSibling;
    parent2 = elm2.parentNode;
    next2   = elm2.nextSibling;

    parent1.insertBefore(elm2, next1);
    parent2.insertBefore(elm1, next2);
}

function formDataToObject (data) {
    let object = {};
    for (let pair of data.entries()) {
        // Reflect.has in favor of: object.hasOwnProperty(key)
        if(!Reflect.has(object, pair[0])){
            object[pair[0]] = pair[1];
            continue;
        }
        if(!Array.isArray(object[pair[0]])){
            object[pair[0]] = [object[pair[0]]];
        }
        object[pair[0]].push(pair[1]);
    }
    return object;
}

let getUrlParameter = function getUrlParameter(sParam) {
    let sPageURL = window.location.search.substring(1),
        sURLVariables = sPageURL.split('&'),
        sParameterName,
        i;

    for (i = 0; i < sURLVariables.length; i++) {
        sParameterName = sURLVariables[i].split('=');

        if (sParameterName[0] === sParam) {
            return sParameterName[1] === undefined ? true : decodeURIComponent(sParameterName[1]);
        }
    }
};