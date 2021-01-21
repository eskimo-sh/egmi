/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.http.HttpClient;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ProblemManager {

    private static final Logger logger = Logger.getLogger(ProblemManager.class);

    @Value("${remote.egmi.port}")
    private int glusterCommandServerPort = 18999;

    @Autowired
    private GlusterRemoteManager glusterRemoteManager;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private HttpClient httpClient;

    /** For tests */
    void setManagementService (ManagementService managementService) {
        this.managementService = managementService;
    }

    private final Map<String, Problem> problems = new HashMap<>();

    public void addProblem(Problem problem) {
        Problem previous = problems.get (problem.getProblemId());
        if (previous != null) {
            logger.debug ("Problem " + problem.getProblemId() + " is already known");
        } else {
            problems.put (problem.getProblemId(), problem);
        }
    }

    public String getProblemSummary() {
        Map<String, AtomicInteger> problemCounter = new HashMap<>();
        problems.values().forEach(problem -> problemCounter.computeIfAbsent(problem.getProblemType(), (key) -> new AtomicInteger(0)).incrementAndGet());
        return String.join(", ", problemCounter.keySet().stream()
                .map(problemType -> problemCounter.get(problemType).get() + " " + problemType)
                .collect(Collectors.toSet()));
    }

    public void recognize(SystemStatus newStatus) {
        new HashSet<>(problems.keySet()).stream()
                .map (problems::get)
                .filter(problem -> !problem.recognize (newStatus))
                .forEach(problem-> problems.remove(problem.getProblemId()));
    }

    public void resolutionIteration(SystemStatus newStatus) {
        List<Problem> sortedProblems = new ArrayList<>(problems.values());
        Collections.sort(sortedProblems);

        try {
            boolean zeroPrioritySolved = false;
            for (Problem problem : sortedProblems) {

                // If some zero priority problem has been solved, don't try to solve anything else is same iteration
                /*
                if (problem.getPriority() != 0 && zeroPrioritySolved) {
                    return;
                }
                */

                try {
                    // always check leadership before moving forward
                    if (!managementService.isMaster()) {
                        problems.clear();
                        return;
                    }
                    if (problem.solve(glusterRemoteManager, new CommandContext(httpClient, glusterCommandServerPort, managementService))) {
                        problems.remove(problem.getProblemId());

                        if (problem.getPriority() == 0) {
                            zeroPrioritySolved = true;
                        }
                    }
                } catch (ResolutionSkipException e) {
                    logger.error (e, e);
                }
            }
        } catch (ResolutionStopException e) {
            logger.error (e, e);
        }
    }
}
