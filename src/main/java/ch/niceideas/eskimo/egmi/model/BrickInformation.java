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

package ch.niceideas.eskimo.egmi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrickInformation implements Comparable<BrickInformation> {

    private Integer number;
    private String status;
    private String fsType;
    private String device;
    private String free;
    private String total;

    public void setAll(Map<? extends String, ?> brickInformation) {

        for (String key : brickInformation.keySet()) {
            switch (key) {
                case "number":
                    setNumber((Integer)brickInformation.get(key));
                    break;
                case "status":
                    setStatus((String)brickInformation.get(key));
                    break;
                case "fsType":
                    setFsType((String)brickInformation.get(key));
                    break;
                case "device":
                    setDevice((String)brickInformation.get(key));
                    break;
                case "free":
                    setFree((String)brickInformation.get(key));
                    break;
                case "total":
                    setTotal((String)brickInformation.get(key));
                    break;
            }
        }
    }

    @Override
    public int compareTo(BrickInformation info2) {
        if (getNumber() == null) {
            return 1;
        }
        if (info2 == null || info2.getNumber() == null) {
            return -1;
        }
        return getNumber().compareTo(info2.getNumber());
    }
}
