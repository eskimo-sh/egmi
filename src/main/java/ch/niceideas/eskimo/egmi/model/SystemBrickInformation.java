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

package ch.niceideas.eskimo.egmi.model;

import ch.niceideas.common.utils.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class SystemBrickInformation extends AbstractBrickInformation {

    private String numberOverride;
    private String id;
    private Node node;
    private String path;

    @Override
    public void set(String key, Object value) {
        switch (key) {
            case "id":
                setId((String)value);
                break;
            case "node":
                if (value instanceof Node) {
                    setNode((Node) value);
                } else {
                    setNode(Node.from((String) value));
                }
                break;
            case "path":
                setPath((String)value);
                break;
            case "number":
                setNumberOverride((String)value);
                break;
            default:
                super.set(key, value);
                break;
        }
    }

    public void fillIn(JSONObject brickObject) {

        brickObject.put ("number", getNumberOverride());
        brickObject.put ("node", getNode().getAddress());
        brickObject.put ("path", getPath());
        brickObject.put ("id", getId());
        if (StringUtils.isNotBlank(getStatus())) {
            brickObject.put("status", getStatus());
        }
        if (StringUtils.isNotBlank(getDevice())) {
            brickObject.put("device", getDevice());
        }
        if (StringUtils.isNotBlank(getFree())) {
            brickObject.put("free", getFree());
        }
        if (StringUtils.isNotBlank(getTotal())) {
            brickObject.put("tot", getTotal());
        }
    }

    @Override
    public int compareTo(AbstractBrickInformation info2) {
        if (info2 == null) {
            return -1;
        }

        if (!(info2 instanceof SystemBrickInformation)) {
            return super.compareTo(info2);
        }

        if (getNumberOverride() == null) {
            return 1;
        }
        if (((SystemBrickInformation)info2).getNumberOverride() == null) {
            return -1;
        }
        if (StringUtils.isIntegerValue(getNumberOverride()) && StringUtils.isIntegerValue(((SystemBrickInformation)info2).getNumberOverride())) {
            return Integer.valueOf(getNumberOverride()).compareTo(Integer.valueOf(((SystemBrickInformation)info2).getNumberOverride()));
        }

        return getNumberOverride().compareTo(((SystemBrickInformation)info2).getNumberOverride());
    }
}
