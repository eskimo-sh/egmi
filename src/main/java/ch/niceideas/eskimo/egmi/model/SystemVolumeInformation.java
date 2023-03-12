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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class SystemVolumeInformation extends AbstractVolumeInformation {

    private Map<String, String> options;
    private Volume volume;
    private Map<BrickId, SystemBrickInformation> bricks;

    @Override
    public void set(String key, Object value) {
        switch (key) {
            case "options":
                //noinspection unchecked
                setOptions((Map<String, String>) value);
                break;
            case "volume":
                setVolume(Volume.from((String) value));
            case "bricks":
                throw new UnsupportedOperationException();
            default:
                super.set(key, value);
                break;
        }
    }

    public void fillIn(JSONObject volumeObject) {
        volumeObject.put("volume", volume.getName());
        volumeObject.put("status", getStatus());
        volumeObject.put("type", getType());
        volumeObject.put("owner", getOwner());

        volumeObject.put("nb_shards", getNbShards());
        volumeObject.put("nb_replicas", getNbReplicas());
        volumeObject.put("nb_bricks", getNbBricks());

        volumeObject.put("options", new JSONObject(options));

        JSONArray brickArray = null;
        if (volumeObject.has("bricks")) {
            brickArray = (JSONArray) volumeObject.get("bricks");
        } else {
            brickArray = new JSONArray();
            volumeObject.put("bricks", brickArray);
        }

        if (bricks != null) {
            for (SystemBrickInformation brickInfo : bricks.values().stream().sorted().collect(Collectors.toList())){
                JSONObject brickObject = new JSONObject();
                brickInfo.fillIn(brickObject);
                brickArray.put(brickObject);
            };
        }
    }
}
