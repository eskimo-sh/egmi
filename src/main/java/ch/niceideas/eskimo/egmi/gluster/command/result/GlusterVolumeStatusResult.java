/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.egmi.model.BrickInformation;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.log4j.Logger;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class GlusterVolumeStatusResult extends AbstractGlusterResult<GlusterVolumeStatusResult> {

    private static final Logger logger = Logger.getLogger(GlusterVolumeStatusResult.class);

    // parsing stuff
    private static final String SKIP_PREFIX = "Another transaction is in progress for ";
    private static final String IS_NOT_STARTED = "is not started";
    private static final String VOLUME_PREFIX = "Volume ";
    private static final String FILE_SYSTEM_PREFIX = "File System";
    private static final String DEVICE_PREFIX = "Device";
    private static final String ONLINE_PREFIX = "Online";
    private static final String DISK_SPACE_FREE_PREFIX = "Disk Space Free";
    private static final String TOTAL_DISK_SPACE_PREFIX = "Total Disk Space";

    // flags
    public static final String SKIP_TEMP_OP_FLAG = "SKIP_TEMP_OP";
    public static final String VOL_NOT_STARTED_FLAG = "VOL_NOT_STARTED";

    private final Map<String, BrickDetail> brickDetails = new HashMap<>();

    private final GlusterVolumeInfoResult volumeInfo;

    public GlusterVolumeStatusResult (GlusterVolumeInfoResult volumeInfo) {
        this.volumeInfo = volumeInfo;
    }

    public void feedVolumeStatusInStatus(NodeStatus status, int volumeCounter, int brickCounter, String brickId) {

        BrickDetail brickDetail = brickDetails.get(brickId);
        if (brickDetail != null) {
            brickDetail.feedInStatus (status, volumeCounter, brickCounter);
        }
    }

    @Override
    public  GlusterVolumeStatusResult buildFromResponse(HttpClientResponse response) throws HttpClientException {
        if (response.getStatusCode() != 200) {
            setError ("Failed to get an answer from command server");
        } else {
            String brickResult = response.asString(Charset.defaultCharset());
            //System.err.println(volumeResult);

            String currentBrickId = "UNDEFINED";

            for (String line : brickResult.split("\n")) {

                // starting with edge cases
                /* e.g.
                Another transaction is in progress for logstash_data. Please try again after some time.
                Another transaction is in progress for spark_data. Please try again after some time.
                Volume test_2 is not started
                 */

                if (line.startsWith(SKIP_PREFIX)) {
                    String volume = line.substring(SKIP_PREFIX.length(), line.indexOf(".", SKIP_PREFIX.length() + 1)).trim();
                    volumeInfo.overrideStatus (volume, SKIP_TEMP_OP_FLAG);
                    continue;
                }

                if (line.contains(IS_NOT_STARTED)) {
                    String volume = line.substring(VOLUME_PREFIX.length(), line.indexOf(IS_NOT_STARTED)).trim();
                    volumeInfo.overrideStatus (volume, VOL_NOT_STARTED_FLAG);
                    continue;
                }

                if (line.startsWith("Brick")) {
                    String[] split = line.split(":");
                    if (split.length >= 3) {
                        currentBrickId = split[1].trim() + ":" + split[2].trim();
                        if (currentBrickId.startsWith("Brick ")) {
                            currentBrickId = currentBrickId.substring("Brick ".length());
                        }
                    }
                }

                BrickDetail brickDetail = brickDetails.computeIfAbsent(currentBrickId, (brick) -> new BrickDetail());

                String onlineStatus = getBrickInfo(line, ONLINE_PREFIX);
                if (StringUtils.isNotBlank(onlineStatus)) {
                    brickDetail.setStatus(onlineStatus.equals("Y") ? "OK" : "OFFLINE");
                }

                String fsType = getBrickInfo(line, FILE_SYSTEM_PREFIX);
                if (StringUtils.isNotBlank(fsType)) {
                    brickDetail.setFsType(fsType);
                }

                String device = getBrickInfo(line, DEVICE_PREFIX);
                if (StringUtils.isNotBlank(device)) {
                    brickDetail.setDevice(device);
                }

                String free = getBrickInfo(line, DISK_SPACE_FREE_PREFIX);
                if (StringUtils.isNotBlank(free)) {
                    brickDetail.setFree(free);
                }

                String total = getBrickInfo(line, TOTAL_DISK_SPACE_PREFIX);
                if (StringUtils.isNotBlank(total)) {
                    brickDetail.setTotal(total);
                }
            }
        }

        return this;
    }

    private String getBrickInfo(String line, String prefix) {
        if (line.startsWith(prefix)) {
            String[] split = line.split(":");
            if (split.length >= 2) {
                return split[1].trim();
            }
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    private static class BrickDetail extends BrickInformation {

        public void feedInStatus(NodeStatus nodeStatus, int volumeCounter, int brickCounter) {
            nodeStatus.setValueForPath("volumes." + volumeCounter + ".bricks." + brickCounter + ".status", getStatus());
            nodeStatus.setValueForPath("volumes." + volumeCounter + ".bricks." + brickCounter + ".fs_type", getFsType());
            nodeStatus.setValueForPath("volumes." + volumeCounter + ".bricks." + brickCounter + ".device", getDevice());
            nodeStatus.setValueForPath("volumes." + volumeCounter + ".bricks." + brickCounter + ".free", getFree());
            nodeStatus.setValueForPath("volumes." + volumeCounter + ".bricks." + brickCounter + ".total", getTotal());
        }
    }
}
