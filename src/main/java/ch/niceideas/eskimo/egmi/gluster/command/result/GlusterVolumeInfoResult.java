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

package ch.niceideas.eskimo.egmi.gluster.command.result;

import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.common.http.HttpClientResponse;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.model.*;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.log4j.Logger;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor
public class GlusterVolumeInfoResult extends AbstractGlusterResult<GlusterVolumeInfoResult> {

    private static final Logger logger = Logger.getLogger(GlusterVolumeInfoResult.class);

    private static final String VOLUME_NAME = "Volume Name:";
    private static final String TYPE = "Type:";
    private static final String STORAGE_OWNER_UID = "storage.owner-uid:";
    private static final String BRICK_PREFIX = "Brick";
    private static final String BRICKS_LABEL = "Bricks:";

    public static final Pattern BRICKS_REPR_PARSER = Pattern.compile("([0-9]+)( x [(]?([0-9]+)( \\+ ([0-9]+))?[)]? = ([0-9]+))?");

    private final Map<Volume, VolumeInformationWrapper> volumeInfos = new HashMap<>();
    private final Map<Volume, Map<Integer, BrickId>> volumeBricks = new HashMap<>();
    private final Map<Volume, Set<String>> reconfiguredOptions = new HashMap<>();

    public void overrideStatus(Volume volume, String status) {
        Optional.ofNullable(volumeInfos.get(volume))
                .orElseThrow(() -> new IllegalStateException("No GeneralInfo stored for volume " + volume))
                .setStatus(status);
    }

    public List<Volume> getAllVolumes() {
        return volumeInfos.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public Set<String> getVolumeReconfiguredOptions(Volume volume) {
        return reconfiguredOptions.get(volume);
    }

    public void feedVolumeInfoInStatus(NodeStatus status, Volume volume, int counter) {
        VolumeInformationWrapper generalInfo = volumeInfos.get(volume);
        generalInfo.feedInStatus (status, counter);
    }

    public boolean hasBrickIds(Volume volume) {
        return volumeBricks.get(volume) != null && !volumeBricks.get(volume).isEmpty();
    }

    public Set<BrickId> getBrickIds(Volume volume) {
        return new HashSet<>(volumeBricks.get(volume).values());
    }

    public Map<Integer, BrickId> getNumberedBrickIds (Volume volume) {
        return volumeBricks.get(volume);
    }

    @Override
    public  GlusterVolumeInfoResult buildFromResponse(HttpClientResponse response) throws HttpClientException {
        if (response.getStatusCode() != 200) {
            setError ("Failed to get an answer from command server");
        } else {
            String volumeResult = response.asString(Charset.defaultCharset());
            //System.err.println(volumeResult);

            Volume currentVolume = Volume.UNDEFINED;
            boolean parsingOptionsState = false;

            for (String line : volumeResult.split("\n")) {

                if (line.startsWith(VOLUME_NAME)) {
                    currentVolume = Volume.from(line.substring(VOLUME_NAME.length()).trim());
                    parsingOptionsState = false;
                }

                if (currentVolume.equals(Volume.UNDEFINED)) {
                    continue;
                }

                VolumeInformationWrapper volumeInfo = volumeInfos.computeIfAbsent(currentVolume, (key) -> new VolumeInformationWrapper());
                volumeInfo.setStatus ("OK");

                if (line.startsWith(TYPE)) {
                    String type = line.substring(TYPE.length()).trim();
                    volumeInfo.setType(type);
                }

                else if (line.startsWith(STORAGE_OWNER_UID)) {
                    String owner = line.substring(STORAGE_OWNER_UID.length()).trim();
                    volumeInfo.setOwner (owner);
                }

                else if (line.startsWith("Number of Bricks:")) {
                    String bricksRepr = line.substring("Number of Bricks:".length()).trim();

                    Matcher reprMatcher = BRICKS_REPR_PARSER.matcher(bricksRepr);
                    if (reprMatcher.matches()) {

                        if (StringUtils.isBlank(reprMatcher.group(2))) {

                            volumeInfo.setNbShards (reprMatcher.group(1));
                            volumeInfo.setNbBricks (reprMatcher.group(1));

                        } else {

                            volumeInfo.setNbShards(reprMatcher.group(1));
                            volumeInfo.setNbReplicas(reprMatcher.group(3));
                            volumeInfo.setNbArbiters(reprMatcher.group(5));
                            volumeInfo.setNbBricks(reprMatcher.group(6));
                        }

                    } else {
                        logger.warn("CRITICAL : couldn't parse brock representation " + bricksRepr);
                    }
                }

                else if (line.startsWith(BRICK_PREFIX) && !line.trim().equalsIgnoreCase(BRICKS_LABEL)) {


                    String brickNumberString = line.substring(BRICK_PREFIX.length(), line.indexOf(":", BRICK_PREFIX.length()));
                    Integer brickNumber = Integer.valueOf(brickNumberString);

                    String brickDef = line.substring(7).trim();
                    if (StringUtils.isNotBlank(brickDef)) {

                        String[] split = brickDef.split(":");
                        String nodeAddress = split[0];
                        String path = split[1];

                        Map<Integer, BrickId> brickMap = volumeBricks.computeIfAbsent(currentVolume, (key) -> new HashMap<>());
                        brickMap.put(brickNumber, BrickId.fromNodeAndPath(Node.from(nodeAddress), path));

                        //System.err.println (currentVolume +  " - " + node + " - " + path);
                    }
                }

                else if (line.startsWith("Options Reconfigured")) {
                    parsingOptionsState = true;

                }

                else if (parsingOptionsState) {
                    Set<String> options = reconfiguredOptions.computeIfAbsent(currentVolume, (key) -> new HashSet<>());
                    options.add (line);
                }
            }
        }

        return this;
    }

    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    private static class VolumeInformationWrapper extends NodeVolumeInformation {

        public void feedInStatus(NodeStatus nodeStatus, int counter) {
            nodeStatus.setValueForPath("volumes." + counter + ".status", getStatus());
            nodeStatus.setValueForPath("volumes." + counter + ".type", getType());
            nodeStatus.setValueForPath("volumes." + counter + ".owner", getOwner());
            nodeStatus.setValueForPath("volumes." + counter + ".nb_shards", getNbShards());
            nodeStatus.setValueForPath("volumes." + counter + ".nb_replicas", getNbReplicas());
            nodeStatus.setValueForPath("volumes." + counter + ".nb_arbiters", getNbArbiters());
            nodeStatus.setValueForPath("volumes." + counter + ".nb_bricks", getNbBricks());
        }
    }
}
