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
import ch.niceideas.common.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class NodeStatus extends JsonWrapper {

    public NodeStatus(String jsonString)  {
        super (jsonString);
    }

    public boolean isPoolStatusError() {
        String poolError = getValueForPathAsString("pool-status-error");
        return StringUtils.isNotBlank(poolError) && poolError.equals("KO");
    }

    public Set<Node> getAllPeers () throws NodeStatusException {
        if (isPoolStatusError()) {
            throw new NodeStatusException("Pool status fetching failed.");
        }

        JSONArray peerArray = getJSONObject().getJSONArray("peers");
        return peerArray.toList().stream()
                .map(map -> (String) ((Map<?, ?>)map).get("hostname"))
                .map(Node::from)
                .collect(Collectors.toSet());
    }

    public Map<String, Object> getNodeInformation (Node node) throws NodeStatusException{
        if (isPoolStatusError()) {
            throw new NodeStatusException("Pool status fetching failed.");
        }

        JSONArray peerArray = getJSONObject().getJSONArray("peers");

        Map<String, Object> retMap = new HashMap<>();

        peerArray.toList().stream()
                .filter(map -> {
                    String host = (String) ((Map<?, ?>)map).get("hostname");
                    return StringUtils.isNotBlank(host) &&
                            (host.equals ("localhost") || node.matches(host));
                })
                .map(map -> (String) ((Map<?, ?>)map).get("state"))
                .forEach(state -> retMap.put("state", state));

        // identify volume on nodes and count bricks
        Set<String> nodeVolumes = new TreeSet<>();
        int brickCounter = 0;
        if (getJSONObject().has("volumes")) {
            JSONArray volumeArray = getJSONObject().getJSONArray("volumes");
            for (int i = 0; i < volumeArray.length(); i++) {

                JSONObject volume = volumeArray.getJSONObject(i);

                String volumeName = volume.getString("name");
                if (StringUtils.isNotBlank(volumeName)) {

                    JSONArray brickArray = volume.getJSONArray("bricks");
                    for (int j = 0; j < brickArray.length(); j++) {

                        JSONObject brick = brickArray.getJSONObject(j);

                        String brickNode = brick.getString("node");
                        if (StringUtils.isNotBlank(brickNode) && node.matches(brickNode)) {
                            nodeVolumes.add(volumeName);
                            brickCounter++;
                        }
                    }
                }
            }
        }

        retMap.put ("volumes", nodeVolumes);
        retMap.put ("brick_count", brickCounter);

        return retMap;
    }

    public boolean isVolumeStatusError() {
        String volumeError = getValueForPathAsString("volume-status-error");
        return StringUtils.isNotBlank(volumeError) && volumeError.equals("KO");
    }

    public boolean isBrickStatusError() {
        String volumeError = getValueForPathAsString("brick-status-error");
        return StringUtils.isNotBlank(volumeError) && volumeError.equals("KO");
    }

    public Set<Volume> getAllVolumes () throws NodeStatusException {
        if (isVolumeStatusError()) {
            throw new NodeStatusException("Volume status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return Collections.emptySet();
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");
        return volumeArray.toList().stream()
                .map(map -> (String) ((Map<?, ?>)map).get("name"))
                .map(Volume::from)
                .collect(Collectors.toSet());
    }

    public Set<Node> getVolumeNodes (Volume volume) throws NodeStatusException {
        return getVolumeBrickIds(volume).stream()
                .map(BrickId::getNode)
                .collect(Collectors.toSet());
    }

    public Set<BrickId> getVolumeBrickIds(Volume volume) throws NodeStatusException{

        Set<BrickId> retSet = new HashSet<>();

        if (isVolumeStatusError()) {
            throw new NodeStatusException("Volume status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return Collections.emptySet();
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");
        for (int i = 0; i < volumeArray.length(); i++) {

            JSONObject volumeInfo = volumeArray.getJSONObject(i);

            String volumeName = volumeInfo.getString("name");
            if (volume.matches(volumeName)) {

                JSONArray brickArray = volumeInfo.getJSONArray("bricks");
                for (int j = 0; j < brickArray.length(); j++) {

                    JSONObject brick = brickArray.getJSONObject(j);

                    String node = brick.getString("node");
                    String path = brick.getString("path");
                    retSet.add(BrickId.fromNodeAndPath(Node.from(node), path));
                }
            }
        }

        return retSet;
    }

    public Map<BrickId, Volume> getNodeBricksAndVolumes(Node host) throws NodeStatusException{

        Map<BrickId, Volume> retMap = new HashMap<>();

        if (isVolumeStatusError()) {
            throw new NodeStatusException("Volume status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return Collections.emptyMap();
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");
        for (int i = 0; i < volumeArray.length(); i++) {

            JSONObject volume = volumeArray.getJSONObject(i);

            String volumeName = volume.getString("name");
            if (StringUtils.isNotBlank(volumeName)) {

                JSONArray brickArray = volume.getJSONArray("bricks");
                for (int j = 0; j < brickArray.length(); j++) {

                    JSONObject brick = brickArray.getJSONObject(j);

                    String node = brick.getString("node");

                    if (host.matches(node)) {
                        String path = brick.getString("path");
                        retMap.put(BrickId.fromNodeAndPath (Node.from(node), path), Volume.from (volumeName));
                    }
                }
            }
        }

        return retMap;
    }

    public VolumeInformation getVolumeInformation(Volume volume) throws NodeStatusException {
        if (isVolumeStatusError()) {
            throw new NodeStatusException("Volume status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return null;
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");

        VolumeInformation retInfo = new VolumeInformation();

        volumeArray.toList().stream()
                .filter(map -> {
                    String name = (String) ((Map<?, ?>)map).get("name");
                    return volume.matches (name);
                })
                .map(map -> ((Map<?, ?>)map).entrySet())
                .forEach(
                        entries -> entries.stream()
                                .filter( entry -> !entry.getKey().equals("bricks") && !entry.getKey().equals("options"))
                                .forEach(entry -> retInfo.set ((String)entry.getKey(), (String)entry.getValue()) )
                );

        return retInfo;
    }

    public Map<String, String> getReconfiguredOptions (Volume volume) throws NodeStatusException {
        if (isBrickStatusError()) {
            throw new NodeStatusException("Brick status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return Collections.emptyMap();
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");

        Map<String, String> retMap = new HashMap<>();

        volumeArray.toList().stream()
                .filter(map -> {
                    String name = (String) ((Map<?, ?>)map).get("name");
                    return volume.matches(name);
                })
                .map(map -> ((Map<?, ?>)map).entrySet())
                .forEach(
                        entries -> entries.stream()
                                .filter( entry -> entry.getKey().equals("options"))
                                .forEach(entry -> {

                                    @SuppressWarnings("unchecked")
                                    Map<String, String> options = (Map<String, String>) entry.getValue();
                                    for (String optionKey : options.keySet()) {
                                        String optionValue = options.get(optionKey);
                                        retMap.put (optionKey, optionValue);
                                    }
                                })
                );

        return retMap;

    }

    public Map<BrickId, BrickInformation> getVolumeBricksInformation(Volume volume) throws NodeStatusException {
        if (isBrickStatusError()) {
            throw new NodeStatusException("Brick status fetching failed.");
        }

        if (!getJSONObject().has("volumes")) {
            return Collections.emptyMap();
        }

        JSONArray volumeArray = getJSONObject().getJSONArray("volumes");

        Map<BrickId, BrickInformation> retMap = new HashMap<>();

        volumeArray.toList().stream()
                .filter(map -> {
                    String name = (String) ((Map<?, ?>)map).get("name");
                    return volume.matches(name);
                })
                .map(map -> ((Map<?, ?>)map).entrySet())
                .forEach(
                        entries -> entries.stream()
                                .filter( entry -> entry.getKey().equals("bricks"))
                                .forEach(entry -> {

                                    @SuppressWarnings("unchecked")
                                    List<Map<?, ?>> brickInformations = (List<Map<?, ?>>) entry.getValue();
                                    for (Map<?, ?> brickInformation : brickInformations) {
                                        String node = (String) brickInformation.get("node");
                                        String path = (String) brickInformation.get("path");
                                        BrickId brickId = BrickId.fromNodeAndPath(Node.from(node), path);
                                        if (StringUtils.isNotBlank(node) && StringUtils.isNotBlank(path)) {

                                            BrickInformation brickInfo = retMap.computeIfAbsent(brickId, (key) -> new BrickInformation());

                                            //noinspection unchecked
                                            brickInfo.setAll ((Map<? extends String, ?>) brickInformation);

                                            //noinspection unchecked
                                            //brickInfoMap.putAll((Map<? extends String, ? extends String>) brickInformation);
                                        }
                                    }
                                })
                );

        return retMap;
    }
}
