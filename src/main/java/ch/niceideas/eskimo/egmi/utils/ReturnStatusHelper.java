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

package ch.niceideas.eskimo.egmi.utils;

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.common.json.JsonWrapper;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ReturnStatusHelper {

    private ReturnStatusHelper() {}

    public static JsonWrapper createErrorStatus (Exception e) {
        return createErrorStatus(e instanceof  CommonBusinessException ? ((CommonBusinessException)e).getCompleteMessage() : e.getMessage());
    }

    static String buildFullMessage(Exception e) {
        StringBuilder errorMessageBuilder = new StringBuilder();
        errorMessageBuilder.append (e.getMessage());
        Throwable inner = e;
        while ( (inner = inner.getCause()) != null) {
            errorMessageBuilder.append("\n");
            errorMessageBuilder.append(inner.getMessage());
        }
        return errorMessageBuilder.toString();
    }

    public static JsonWrapper createErrorStatus (String errorMessage) {

        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "KO");
                put("error", errorMessage);
            }}));
        } catch (JSONException e1) {
            // cannot happen
            throw new ErrorStatusException(e1);
        }
    }

    public static JsonWrapper createOKStatus() {
        return createOKStatus(map -> {});
    }

    public interface MapFeeder {
        void feedMap (Map<String, Object> map) throws JSONException;
    }

    public static JsonWrapper createOKStatus(MapFeeder additionalAttributesFeeder) {
        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                additionalAttributesFeeder.feedMap (this);
            }}));
        } catch (JSONException e) {
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    public static JsonWrapper createClearStatus (String flag, boolean processingPending, MapFeeder additionalAttributesFeeder) {

        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("processingPending", processingPending);
                put("clear", flag);
                additionalAttributesFeeder.feedMap (this);
            }}));
        } catch (JSONException e1) {
            // cannot happen
            throw new ErrorStatusException(e1);
        }
    }

    public static JsonWrapper createEncodedErrorStatus (Exception e) {

        String errorMessageBuilder = buildFullMessage(e);

        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "KO");
                put("error", Base64.getEncoder().encodeToString(errorMessageBuilder.getBytes()));
            }}));
        } catch (JSONException e1) {
            // cannot happen
            throw new ErrorStatusException(e1);
        }
    }

    public static JsonWrapper createClearStatus (String flag, boolean processingPending) {

        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("processingPending", processingPending);
                put("clear", flag);
            }}));
        } catch (JSONException e1) {
            // cannot happen
            throw new ErrorStatusException(e1);
        }
    }

    public static JsonWrapper createClearStatusWithMessage (String flag, boolean processingPending, String message) {

        try {
            return new JsonWrapper (new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("processingPending", processingPending);
                put("clear", flag);
                put("message", message);
            }}));
        } catch (JSONException e1) {
            // cannot happen
            throw new ErrorStatusException(e1);
        }
    }

    public static class ErrorStatusException extends RuntimeException {

        static final long serialVersionUID = -331151212312431248L;

        ErrorStatusException(Throwable cause) {
            super(cause);
        }
    }
}
