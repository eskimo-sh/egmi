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
import ch.niceideas.eskimo.egmi.model.Node;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor
public class GlusterPoolListResult extends AbstractGlusterResult<GlusterPoolListResult> {

    private static final Pattern POOL_LIST_PARSER = Pattern.compile("([a-zA-Z0-9\\-._]+)[ \\t]+([a-zA-Z0-9\\-._]+)[ \\t]+([a-zA-Z0-9\\-._]+)");

    private final List<GlusterPeerEntry> entryList = new ArrayList<>();

    public void addEntry (String uid, String hostname, String state) {
        entryList.add(new GlusterPeerEntry(uid, hostname, state));
    }

    public int size() {
        return entryList.size();
    }

    public String getUid(int row) {
        if (row > size()) {
            throw new IllegalArgumentException();
        }
        return entryList.get(row).getUid();
    }

    public String getHostname(int row) {
        if (row > size()) {
            throw new IllegalArgumentException();
        }
        return entryList.get(row).getHostname();
    }

    public List<String> getAllHosts() {
        return entryList.stream()
                .map(GlusterPeerEntry::getHostname)
                .collect(Collectors.toList());
    }

    public String getState(int row) {
        if (row > size()) {
            throw new IllegalArgumentException();
        }
        return entryList.get(row).getState();
    }

    @Override
    public  GlusterPoolListResult buildFromResponse(HttpClientResponse response) throws HttpClientException {
        if (response.getStatusCode() != 200) {
            setError ("Failed to get an answer from command server");
        } else {
            String poolResult = response.asString(Charset.defaultCharset());
            //System.err.println(poolResult);

            Matcher poolListMatcher = POOL_LIST_PARSER.matcher(poolResult);
            while (poolListMatcher.find()) {
                String uid = poolListMatcher.group(1);
                if (!uid.trim().equalsIgnoreCase("UUID")) {
                    String hostname = poolListMatcher.group(2);
                    String state = poolListMatcher.group(3);
                    addEntry (uid, hostname, state);
                }
            }
        }

        return this;
    }

    public boolean isSuccess() {
        return !isError();
    }

    public boolean contains(Node host) {
        return getAllHosts().contains(host.getAddress());
    }

    @Data
    @AllArgsConstructor
    private static class GlusterPeerEntry {
        private String uid;
        private String hostname;
        private String state;
    }
}
