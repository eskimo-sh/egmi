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

import ch.niceideas.common.json.JsonWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SystemStatus extends JsonWrapper {

    public SystemStatus(String jsonString)  {
        super (jsonString);
    }

    private JSONArray getVolumes() {
        return getJSONObject().getJSONArray("volumes");
    }

    private JSONArray getNodes() {
        return getJSONObject().getJSONArray("nodes");
    }

    public List<Node> getNodeList() {
        JSONArray nodeArray = getNodes();
        return IntStream.range(0, nodeArray.length())
                .mapToObj(nodeArray::getJSONObject)
                .map(nodeInfo -> nodeInfo.getString("host"))
                .map(Node::from)
                .collect(Collectors.toList());
    }

    protected JSONObject getVolumeInfo(Volume volume) {
        JSONArray volumeArray = getVolumes();
        return IntStream.range(0, volumeArray.length())
                .mapToObj(volumeArray::getJSONObject)
                .filter(volumeInfo -> volume.matches(volumeInfo.getString("volume")))
                .findAny().orElse(null);
    }

    protected JSONObject getNodeInfo(Node host) {
        JSONArray nodeArray = getNodes();
        return IntStream.range(0, nodeArray.length())
                .mapToObj(nodeArray::getJSONObject)
                .filter(nodeInfo -> host.matches (nodeInfo.getString("host")))
                .findAny().orElse(null);
    }

    protected JSONArray getBrickArray (Volume volume) {
        JSONArray volumeArray = getVolumes();
        return IntStream.range(0, volumeArray.length())
                .mapToObj(volumeArray::getJSONObject)
                .filter(volumeInfo -> volume.matches (volumeInfo.getString("volume")))
                .map(volumeInfo -> volumeInfo.getJSONArray("bricks"))
                .findAny().orElse(null);
    }

    protected JSONObject getOptions(Volume volume) {
        JSONArray volumeArray = getVolumes();
        return IntStream.range(0, volumeArray.length())
                .mapToObj(volumeArray::getJSONObject)
                .filter(volumeInfo -> volume.matches(volumeInfo.getString("volume")))
                .map(volumeInfo -> volumeInfo.getJSONObject("options"))
                .findAny().orElse(null);
    }

    protected JSONObject getBrickInfo (Volume volume, BrickId brickId) {
        JSONArray brickArray = getBrickArray(volume);

        if (brickArray == null) {
            return null;
        }

        return IntStream.range(0, brickArray.length())
                .mapToObj(brickArray::getJSONObject)
                .filter(brickInfo -> brickInfo.getString("id").equals(brickId.toString()))
                .findAny().orElse(null);
    }

    public String getNodeStatus(Node host) {
        JSONObject nodeInfo = Optional.ofNullable(getNodeInfo(host)).orElseThrow(IllegalStateException::new);
        if (nodeInfo.has("status")) {
            return nodeInfo.getString("status");
        } else {
            return null;
        }
    }

    public void overrideNodeStatus(Node host, String newStatus) {
        JSONObject nodeInfo = Optional.ofNullable(getNodeInfo(host)).orElseThrow(IllegalStateException::new);
        nodeInfo.put("status", newStatus);
    }

    public String getBrickStatus(Volume volume, BrickId brickId) {
        JSONObject brickInfo = getBrickInfo(volume, brickId);
        if (brickInfo == null) {
            return null;
        }
        return brickInfo.getString("status");
    }

    public String getOptionValue(Volume volume, String optionKey) {
        JSONObject options = getOptions(volume);

        if (!options.has(optionKey)) {
            return null;
        }

        return options.getString(optionKey);
    }

    public Integer getNumberOfBricks(Volume volume) {
        JSONArray brickArray = getBrickArray(volume);
        if (brickArray == null) {
            return null;
        }
        return brickArray.length();
    }

    public boolean hasBricksOnNode(Volume volume, Node host) {
        JSONArray brickArray = getBrickArray(volume);
        if (brickArray == null) {
            return false;
        }

        return IntStream.range(0, brickArray.length())
                .mapToObj(brickArray::getJSONObject)
                .anyMatch(brickInfo -> brickInfo.getString("id").contains(host.getAddress()));
    }

    public String getVolumeStatus(Volume volume) {
        JSONObject volumeInfo = getVolumeInfo(volume);
        if (volumeInfo == null) {
            return null;
        }

        return volumeInfo.getString("status");
    }

    public void addNodeInfo(Node node, String status, NodeInformation nodeInfo) {

        JSONArray nodeArray;
        if (!getJSONObject().has("nodes")) {
            nodeArray = new JSONArray();
            getJSONObject().put("nodes", nodeArray);
        } else {
            nodeArray = getJSONObject().getJSONArray("nodes");
        }

        JSONObject volumeObject = new JSONObject();
        nodeArray.put(volumeObject);

        String volumes = null;
        Serializable brickCount = null;

        if (nodeInfo != null) {
            Set<String> nodeVolumes = nodeInfo.getVolumes();
            if (nodeVolumes != null) {
                volumes = String.join(", ", nodeVolumes);
            } else {
                volumes = "?";
            }

            Integer nodeBrickCount =  nodeInfo.getBrickCount();
            brickCount = Objects.requireNonNullElse(nodeBrickCount, "?");
        }

        volumeObject.put ("host", node.getAddress());
        volumeObject.put ("status", status);
        volumeObject.put ("volumes", volumes);
        volumeObject.put ("nbr_bricks", brickCount);
    }

    public void addVolumeInfo(SystemVolumeInformation systemVolumeInfo) {

        JSONArray volumeArray;
        if (!getJSONObject().has("volumes")) {
            volumeArray = new JSONArray();
            getJSONObject().put("volumes", volumeArray);
        } else {
            volumeArray = getJSONObject().getJSONArray("volumes");
        }

        JSONObject volumeObject = new JSONObject();
        systemVolumeInfo.fillIn (volumeObject);
        volumeArray.put(volumeObject);
    }
}
