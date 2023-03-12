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

package ch.niceideas.eskimo.egmi.management;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeStatusResult;
import ch.niceideas.eskimo.egmi.model.*;
import ch.niceideas.eskimo.egmi.problems.*;
import ch.niceideas.eskimo.egmi.zookeeper.ZookeeperService;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.Serializable;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ManagementService implements ResolutionLogger, RuntimeSettingsOwner, ConfigurationOwner {

    private static final Logger logger = Logger.getLogger(ManagementService.class);

    public static final String RUNTIME_CONFIG_JSON_PATH = "/egmi-runtime-config.json";

    private final ReentrantLock runtimeConfigLock = new ReentrantLock();

    @Autowired
    private GlusterRemoteManager glusterRemoteManager;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private ProblemManager problemManager;

    @Autowired
    private Environment env;

    @Autowired
    private ZookeeperService zookeeperService;

    protected String runtimeConfiguredNodes;

    protected String testConfiguredNodes = "";

    @Value("${target.predefined-ip-addresses:#{null}}")
    protected String preConfiguredNodes = "";

    @Value("${target.volumes}")
    private String configuredVolumes = "";

    @Value("${config-storage-path}")
    private String configStoragePath = null;

    @Value("${server.servlet.context-path:''}")
    @Getter
    private String contextRoot = null;

    @Value("${system.statusUpdatePeriodSeconds}")
    private int statusUpdatePeriodSeconds = 30;

    @Value("${target.numberOfBricks}")
    protected String targetNumberBricksString = "LOG_DISPATCH";

    @Value("${target.defaultNumberReplica}")
    protected int defaultNumberReplica=3;

    @Value("${target.volumes.performance.off}")
    private String volumesPerformanceOff;

    @Value("${config.performance.off}")
    private String performanceOffOptions;

    private final ScheduledExecutorService statusRefreshScheduler;
    private final ReentrantLock statusUpdateLock = new ReentrantLock();
    private final AtomicReference<SystemStatus> lastStatus = new AtomicReference<>();
    private final AtomicReference<Exception> lastStatusException = new AtomicReference<>();

    private final ThreadLocal<SimpleDateFormat> formatLocal = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));

    /** for tests */
    public void setGlusterRemoteManager (GlusterRemoteManager glusterRemoteManager) {
        this.glusterRemoteManager = glusterRemoteManager;
    }
    public void setMessagingService (MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    public void setProblemManager (ProblemManager problemManager) {
        this.problemManager = problemManager;
    }
    public void setZoopeeerService (ZookeeperService zookeeperService) {
        this.zookeeperService = zookeeperService;
    }
    public void setTestConfig(String configuredNodes, String configuredVolumes) {
        this.runtimeConfiguredNodes = null;
        this.testConfiguredNodes = configuredNodes;
        this.configuredVolumes = configuredVolumes;
    }
    public void setTestConfigStoragePath (String configStoragePath) {
        this.configStoragePath = configStoragePath;
    }
    public void setTargetNumberBricksString(String targetNbrBricks) {
        this.targetNumberBricksString = targetNbrBricks;
    }
    public void setDefaultNumberReplica (int defaultNumberReplica) {
        this.defaultNumberReplica = defaultNumberReplica;
    }

    // constructor for spring
    public ManagementService() {
        this (true);
    }
    // constructor for tests
    public ManagementService(boolean createUpdateScheduler) {
        if (createUpdateScheduler) {

            // I shouldn't use a timer here since scheduling at fixed interval may lead to flooding the system and ending
            // up in doing only this on large clusters

            statusRefreshScheduler = Executors.newSingleThreadScheduledExecutor();

            logger.info("Initializing Status updater scheduler ...");
            statusRefreshScheduler.schedule(this::updateSystemStatus, 5, TimeUnit.SECONDS); // start ASAP
        } else {
            statusRefreshScheduler = null;
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info ("Cancelling status updater scheduler");
        if (statusRefreshScheduler != null) {
            statusRefreshScheduler.shutdownNow();
        }
    }

    public Set<Volume> getVolumesPerformanceOff() {
        if (volumesPerformanceOff == null || StringUtils.isBlank(volumesPerformanceOff.trim())) {
            return Collections.emptySet();
        }

        return Arrays.stream(volumesPerformanceOff.split(","))
                .map(Volume::from)
                .collect(Collectors.toSet());
    }

    public Set<String> getPerformanceOffOptions() {
        if (performanceOffOptions == null || StringUtils.isBlank(performanceOffOptions.trim())) {
            return Collections.emptySet();
        }

        Set<String> retSet = new HashSet<>();
        Collections.addAll(retSet, performanceOffOptions.split(","));
        return retSet;
    }

    protected String getRTConfiguredNodes() {
        if (StringUtils.isBlank(runtimeConfiguredNodes)) {
            if (StringUtils.isNotBlank(testConfiguredNodes)) {
                runtimeConfiguredNodes = testConfiguredNodes;
            } else if (StringUtils.isNotBlank(preConfiguredNodes)) {
                runtimeConfiguredNodes = preConfiguredNodes;
            } else {
                runtimeConfiguredNodes = zookeeperService.getConfiguredNodes();
            }
        }
        return runtimeConfiguredNodes;
    }

    public boolean isMaster() {
        return zookeeperService.isMaster();
    }

    public SystemStatus getSystemStatus() throws ManagementException {

        // special case at application startup : if the UI request comes before the first status update
        if (lastStatusException.get() == null && lastStatus.get() == null) {
            return new SystemStatus("{ \"clear\" : \"initializing\"}");
        }

        if (lastStatusException.get() != null) {
            throw new ManagementException (lastStatusException.get());
        }
        return lastStatus.get();
    }

    public void updateSystemStatus() {

        logger.info ("- Updating System Status");

        if (!zookeeperService.isMaster()) {
            logger.info ("  + Not updating status since I am no master");
            if (statusRefreshScheduler != null) {
                statusRefreshScheduler.schedule(this::updateSystemStatus, statusUpdatePeriodSeconds, TimeUnit.SECONDS);
            }
            return;
        }

        int effectiveStatusUpdatePeriodSeconds = statusUpdatePeriodSeconds;
        try {
            statusUpdateLock.lock();
            logger.info ("  + Got lock - proceeding ...");

            // refresh available data nodes from zookeeper
            if (StringUtils.isNotBlank(testConfiguredNodes)) {
                runtimeConfiguredNodes = testConfiguredNodes;
            } else if (StringUtils.isNotBlank(preConfiguredNodes)) {
                runtimeConfiguredNodes = preConfiguredNodes;
            } else {
                runtimeConfiguredNodes = zookeeperService.getConfiguredNodes();
            }

            Map<Node, NodeStatus> nodesStatus = glusterRemoteManager.getAllNodeStatus();

            // 1. Build complete set of nodes and volumes
            Set<Node> allNodes = getRuntimeNodes(nodesStatus);

            Set<Volume> allVolumes = getRuntimeVolumes (nodesStatus);

            // -- problem detection phase
            SystemStatus newStatus = getSystemStatus(InetAddress.getLocalHost().toString(), nodesStatus, allNodes, allVolumes);

            // 3. Detection connection graph partitioning
            GraphPartitionDetector.detectGraphPartitioning (problemManager, allNodes, newStatus, nodesStatus);

            // 4. Detect peer connection inconsistencies
            detectConnectionInconsistencies (problemManager, allNodes, nodesStatus, newStatus);

            // -- END OF problem detection phase

            // 5. Update problems
            // -- problem recognition (validation) phase
            problemManager.recognize (newStatus);
            // -- end of problem recognition (validation) phase

            // 6. Store status for UI
            info("Status fetching completed. " + problemManager.getProblemSummary());

            lastStatus.set (newStatus);
            lastStatusException.set (null);

            // 7. Problem resolution iteration
            try {
                if (problemManager.resolutionIteration(newStatus)) {
                    effectiveStatusUpdatePeriodSeconds = statusUpdatePeriodSeconds / 3; // shorten resolution loop if a problem has been solved
                }
            } catch (Exception e) {
                error(e);
            }

            // FIXME : in addition, a node being down for more than a few hours, once all bricks have been recreated elsewhere,
            // should be removed from runtime node list.
            // (such case wouldn0t be tracked by NodeDown problem since it owns no bricks anymore so I need to come up with something here !)


        } catch (Exception e) {
            error(e);

            lastStatusException.set(e);
            lastStatus.set(null);

        } finally {
            statusUpdateLock.unlock();
            // reschedule
            if (statusRefreshScheduler != null) {
                statusRefreshScheduler.schedule(this::updateSystemStatus, effectiveStatusUpdatePeriodSeconds, TimeUnit.SECONDS);
            }
        }
    }

    SystemStatus getSystemStatus(String hostName, Map<Node, NodeStatus> nodesStatus, Set<Node> allNodes, Set<Volume> allVolumes) throws NodeStatusException {

        SystemStatus newStatus = new SystemStatus("{\"hostname\" : \"" + hostName + "\"}");

        // 1. Build Node status
        buildNodeInfo(nodesStatus, allNodes, newStatus);

        // 2. Build Volume status
        buildVolumeInfo(nodesStatus, allNodes, allVolumes, newStatus);

        return newStatus;
    }

    private void detectConnectionInconsistencies(ProblemManager problemManager, Set<Node> allNodes, Map<Node, NodeStatus> nodesStatus, SystemStatus newStatus) {

        try {
            // 1. Build all nodes neighbours
            Map<Node, Set<Node>> nodesPeeers = new HashMap<>();
            for (Node node : allNodes) {

                NodeStatus nodeStatus = nodesStatus.get(node);
                if (nodeStatus != null) { // don't act on node down
                    nodesPeeers.put (node, nodeStatus.getAllPeers());
                }
            }

            // 2. Find inconsistencies
            for (Node node : allNodes) {
                Set<Node> nodePeers = nodesPeeers.get(node);
                if (nodePeers != null) {
                    for (Node other : allNodes) {
                        Set<Node> otherPeers = nodesPeeers.get(other);
                        if (otherPeers != null) {

                            if (otherPeers.contains(node) && !nodePeers.contains(other)) {
                                if (flagNodeInconsistent(problemManager, newStatus, node, other)) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

        } catch (NodeStatusException e) {
            logger.error (e, e);
        }

    }

    private boolean flagNodeInconsistent(ProblemManager problemManager, SystemStatus newStatus, Node node, Node other) {
        // flag node inconsistent
        String prevStatus =  newStatus.getNodeStatus(node);
        if (StringUtils.isBlank(prevStatus) || !prevStatus.equals("KO")) { // don't overwrite KO node
            newStatus.overrideNodeStatus (node, "INCONSISTENT");
            problemManager.addProblem(new NodeInconsistent(new Date(), node, other));
            return true;
        }
        return false;

    }

    private void buildVolumeInfo(Map<Node, NodeStatus> nodesStatus,
                                             Set<Node> allNodes, Set<Volume> allVolumes, SystemStatus newStatus)
            throws NodeStatusException {

        int targetNbrBricks = getTargetNumberOfBricks();
        int targetNbrReplicas = getTargetNumberOfReplicas();
        int targetNbrShards = targetNbrBricks / targetNbrReplicas;

        for (Volume volume : allVolumes.stream().sorted().collect(Collectors.toList())) {
            SystemVolumeInformation systemVolumeInfo = new SystemVolumeInformation();
            systemVolumeInfo.setVolume(volume);

            Set<String> errors = new HashSet<>();

            // get volume information from all nodes
            Map<String, String> options = new HashMap<>();

            Map<BrickId, SystemBrickInformation> bricksInfo = new HashMap<>();

            for (Node node : allNodes) {

                NodeStatus nodeStatus = nodesStatus.get(node);
                if (nodeStatus != null) {

                    NodeVolumeInformation nodeVolumeInfo = nodeStatus.getVolumeInformation(volume);

                    String type = nodeVolumeInfo != null ? nodeVolumeInfo.getType() : null;
                    String owner = nodeVolumeInfo != null ? nodeVolumeInfo.getOwner() : null;
                    String volStatus = nodeVolumeInfo != null ? nodeVolumeInfo.getStatus() : null;
                    String effNbShards = nodeVolumeInfo != null ? nodeVolumeInfo.getNbShards() : null;
                    String effNbReplicas = nodeVolumeInfo != null ? nodeVolumeInfo.getNbReplicas() : null;
                    String effNbArbiters = nodeVolumeInfo != null ? nodeVolumeInfo.getNbArbiters() : null;
                    String effNbBricks = nodeVolumeInfo != null ? nodeVolumeInfo.getNbBricks() : null;

                    String nbReplicas = StringUtils.isBlank(effNbReplicas) ? null : (StringUtils.isNotBlank(effNbArbiters) ?
                            "(" + effNbReplicas + " + " + effNbArbiters + ") / " + targetNbrReplicas :
                            effNbReplicas + " / " + targetNbrReplicas);

                    String nbShards = StringUtils.isBlank(effNbShards) ? null : (effNbShards + " / " + targetNbrShards);
                    String nbBricks = StringUtils.isBlank(effNbBricks) ? null : (effNbBricks + " / " + targetNbrBricks);

                    setInVolumeStatus(systemVolumeInfo, "type", type, systemVolumeInfo.getType(), true, errors, "TYPES");

                    setInVolumeStatus(systemVolumeInfo, "owner", owner, systemVolumeInfo.getOwner(), true, errors, "OWNER");

                    setInVolumeStatus(systemVolumeInfo, "nb_shards", nbShards, systemVolumeInfo.getNbShards(), false, errors, "NB. SHARDS");

                    setInVolumeStatus(systemVolumeInfo, "nb_replicas", nbReplicas, systemVolumeInfo.getNbReplicas(), false, errors, "NB. REPL.");

                    setInVolumeStatus(systemVolumeInfo, "nb_bricks", nbBricks, systemVolumeInfo.getNbBricks(), false, errors, "NB. BRICKS");

                    // volume options management
                    Map<String, String> nodeOptions = nodeStatus.getReconfiguredOptions(volume);
                    for (String optionKey : nodeOptions.keySet()) {
                        String nodeOptionValue = nodeOptions.get(optionKey);

                        String prevValue = options.get(optionKey);
                        if (StringUtils.isBlank(prevValue)) {
                            options.put(optionKey, nodeOptionValue);
                        } else if (!prevValue.equals(nodeOptionValue)) {
                            errors.add("DIFB. OPTIONS");
                            notifyInconsistency(" - got option value " + nodeOptionValue + " while previous node had value " + prevValue);
                        }
                    }

                    // volume brick management
                    Map<BrickId, NodeBrickInformation> nodeBricksInfo = nodeStatus.getVolumeBricksInformation(volume);
                    List<BrickId> brickIdList = new ArrayList<>(nodeBricksInfo.keySet());
                    brickIdList.sort(new BrickIdNumberComparator (nodeBricksInfo));

                    for (BrickId brickId : brickIdList) {

                        NodeBrickInformation nodeBrickInfo = nodeBricksInfo.get(brickId);

                        SystemBrickInformation brickInfo = bricksInfo.computeIfAbsent(brickId, (newId) -> new SystemBrickInformation());

                        setInBrickInfo(brickInfo, "id", brickId.toString(), brickInfo.getId(), errors, "ID");

                        Integer effNumberInt = nodeBrickInfo != null ? nodeBrickInfo.getNumber() : null;
                        String effNumber = "?";
                        if (effNumberInt != null) {
                            effNumber = effNumberInt.toString();
                        }
                        setInBrickInfo(brickInfo, "number", effNumber, brickInfo.getNumberOverride(), errors, "NBR");

                        setInBrickInfo(brickInfo, "node", brickId.getNode(), brickInfo.getNode(), errors, "NODE");

                        setInBrickInfo(brickInfo, "path", brickId.getPath(), brickInfo.getPath(), errors, "PATH");

                        if (StringUtils.isNotBlank(volStatus) && volStatus.equals(GlusterVolumeStatusResult.VOL_NOT_STARTED_FLAG)) { // this is set global, no point in continuing
                            errors = new HashSet<>();
                            errors.add("NOT STARTED ");
                            problemManager.addProblem (new VolumeNotStarted(new Date(), volume));
                            continue;
                        }

                        if (nodeBrickInfo != null && (StringUtils.isBlank(volStatus) || !volStatus.contains("TEMP"))) {

                            String effStatus = nodeBrickInfo.getStatus();
                            if (effStatus != null && effStatus.equals("OFFLINE")) {
                                errors.add ("BRICK OFFLINE");
                                problemManager.addProblem (new BrickOffline(new Date(), volume, brickId));
                            }

                            setInBrickInfo(brickInfo, "status", effStatus, brickInfo.getStatus(), errors, "STATUS");

                            setInBrickInfo(brickInfo, "device", nodeBrickInfo.getDevice(), brickInfo.getDevice(), errors, "DEV");

                            brickInfo.set("free", nodeBrickInfo.getFree());

                            brickInfo.set("tot", nodeBrickInfo.getTotal());
                        }
                    }
                }
            }

            // Post status building consistency checks

            // If not type was found, the volume is likely not configured at all
            if (StringUtils.isBlank(systemVolumeInfo.getType())) {
                errors.add("NO VOLUME");
                problemManager.addProblem (new NoVolume(new Date(), volume));

            } else {

                if (bricksInfo.size() < targetNbrBricks) {
                    errors.add("MISSING " + (targetNbrBricks - bricksInfo.size()) + " BRICKS");
                    problemManager.addProblem (new MissingBrick(new Date(), volume, targetNbrBricks, bricksInfo.size()));
                }

                // check options matching performance disablement
                if (getVolumesPerformanceOff().contains(volume)) {
                    for (String optionToTurnOff : getPerformanceOffOptions()) {

                        String optionValue = options.get(optionToTurnOff.replace(".", "__"));
                        if (StringUtils.isBlank(optionValue) || !optionValue.trim().equals("off")) {

                            problemManager.addProblem (new WrongOption(new Date(), volume, optionToTurnOff, optionValue, "off"));
                            errors.add(volume + " WRONG OPTION " + optionToTurnOff + "/" + optionValue);
                        }
                    }
                }
            }

            // Check if a brick could not be build at all
            for (BrickId brickId: bricksInfo.keySet()) {
                SystemBrickInformation brickInfo = bricksInfo.get(brickId);
                String status = brickInfo.getStatus();
                if (StringUtils.isBlank(status)) {
                    Node node = brickInfo.getNode();
                    if (node != null) {
                        // find nodeInfo
                        String nodeStatus = newStatus.getNodeStatus(node);
                        if (StringUtils.isBlank(nodeStatus) || nodeStatus.equals("KO")) {
                            errors.add(node + " DOWN");
                            problemManager.addProblem (new NodeDown(new Date(), volume, node));
                            problemManager.addProblem (new NodeDownRemoval(new Date(), node));
                        }
                    }
                }
            }

            systemVolumeInfo.setStatus(errors.size() == 0 ? "OK" : String.join(" / ", errors));

            systemVolumeInfo.setBricks(bricksInfo);

            systemVolumeInfo.setOptions(options);

            newStatus.addSystemInfo (systemVolumeInfo);
        }
    }

    private void setInBrickInfo(SystemBrickInformation brickInfo, String attribute, Object value, Object previous, Set<String> errors, String errorTag) {
        if (previous == null || StringUtils.isBlank(""+previous)) {
            brickInfo.set(attribute, value);
        } else if (!previous.equals(value)) {
            errors.add("DIFB. " + errorTag);
            notifyInconsistency(" - brickId " + brickInfo.getId() + " got " + value + " while value for previous node was " + previous);
        }
    }

    private void setInVolumeStatus(SystemVolumeInformation systemVolumeInfo, String attribute, String value, String previous, boolean append, Set<String> errors, String errorTag) {
        if (StringUtils.isNotBlank(value)) {
            if (StringUtils.isBlank(previous)) {
                systemVolumeInfo.set(attribute, value);
            } else if (!previous.equals(value)) {
                errors.add("DIF " + errorTag);
                if (append) {
                    systemVolumeInfo.set(attribute, previous + "," + value);
                } else {
                    notifyInconsistency(" - for volume " + systemVolumeInfo.getVolume()
                            + " - got " + attribute + "  " + value + " while value for previous node was " + previous);
                }
            }
        }
    }

    public int getTargetNumberOfBricks() {

        // CAUTION : number of bricks must be a multiple of replica set

        int targetNumberOfBricks = getTheoreticalNumberOfBricks();

        int numberOfReplicas = getTargetNumberOfReplicas();

        while (targetNumberOfBricks % numberOfReplicas != 0) {
            targetNumberOfBricks--;
        }

        return targetNumberOfBricks;
    }

    private int getTheoreticalNumberOfBricks() {
        int configuredNumberOfNodes = getRTConfiguredNodes().split(",").length;

        int theoreticalNbrBricks = 0;

        if (StringUtils.isBlank(targetNumberBricksString) || targetNumberBricksString.equals("LOG_DISPATCH")) {

            if (configuredNumberOfNodes == 1) {
                theoreticalNbrBricks = 1;
            } else if (configuredNumberOfNodes == 2) {
                 theoreticalNbrBricks = 2;
            } else if (configuredNumberOfNodes >= 3) {
                theoreticalNbrBricks = (int) Math.round (2 + Math.log(configuredNumberOfNodes));
            }

        } else if (targetNumberBricksString.equals("ALL") || targetNumberBricksString.equals("ALL_NODES")) {
            theoreticalNbrBricks = configuredNumberOfNodes;

        } else {
            theoreticalNbrBricks = Integer.parseInt(targetNumberBricksString);
            if (theoreticalNbrBricks > configuredNumberOfNodes) {
                theoreticalNbrBricks = configuredNumberOfNodes;
            }
        }
        return theoreticalNbrBricks;
    }

    public int getTargetNumberOfReplicas() {

        int targetNumberOfBricks = getTheoreticalNumberOfBricks();
        if (targetNumberOfBricks == 0) {
            return 0;
        } else if (targetNumberOfBricks == 1) {
            return 1;
        }

        int numberOfReplicas = defaultNumberReplica;

        while (targetNumberOfBricks != numberOfReplicas && targetNumberOfBricks / 2 < numberOfReplicas) {
            numberOfReplicas--;
        }

        return numberOfReplicas;
    }

    private void notifyInconsistency(String s) {
        logger.warn (s);
        messagingService.addLine(getMessageDate() + " - WARN: " + s);
    }

    void buildNodeInfo(Map<Node, NodeStatus> nodesStatus, Set<Node> allNodes, SystemStatus systemStatus) throws NodeStatusException {

        int counter = 0;
        for (Node node : allNodes) {

            String status = "KO";
            String volumes = null;
            Serializable brickCount = null;

            NodeStatus nodeStatus = nodesStatus.get(node);
            if (nodeStatus != null) {

                if (nodeStatus.isPoolStatusError()) {
                    status = "KO";
                } else {
                    status = "OK";
                    Map<String, Object> nodeInfo = nodeStatus.getNodeInformation(node);

                    @SuppressWarnings("unchecked")
                    Set<String> nodeVolumes = (Set<String>) nodeInfo.get("volumes");
                    if (nodeVolumes != null) {
                        volumes = String.join(", ", nodeVolumes);
                    } else {
                        volumes = "?";
                    }

                    Integer nodeBrickCount =  (Integer) nodeInfo.get("brick_count");
                    brickCount = Objects.requireNonNullElse(nodeBrickCount, "?");
                }
            }

            systemStatus.setValueForPath("nodes." + counter + ".host", node.getAddress());
            systemStatus.setValueForPath("nodes." + counter + ".status", status);
            systemStatus.setValueForPath("nodes." + counter + ".volumes", volumes);
            systemStatus.setValueForPath("nodes." + counter + ".nbr_bricks", brickCount);

            counter++;
        }
    }

    public Set<Node> getAllNodes() throws ManagementException {

        Set<Node> nodes = getConfiguredNodes();

        // 2. Add all previously discovered nodes
        JsonWrapper runtimeConfig = loadRuntimeSettings();
        if (!runtimeConfig.isEmpty()) {
            injectRuntimeNodes(nodes, runtimeConfig.getValueForPathAsString("discovered-nodes"));
            injectRuntimeNodes(nodes, runtimeConfig.getValueForPathAsString("static-nodes"));
        }

        return nodes;
    }

    private void injectRuntimeNodes(Set<Node> nodes, String runtimeNodes) {
        if (StringUtils.isNotBlank(runtimeNodes)) {
            String[] discNodes = runtimeNodes.split(",");
            Arrays.stream(discNodes)
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .map(Node::from)
                    .forEach(nodes::add);
        }
    }

    public Set<Node> getConfiguredNodes() {
        Set<Node> nodes = new HashSet<>();

        // 1. Add all configured nodes
        String[] confNodes = getRTConfiguredNodes().split(",");
        Arrays.stream(confNodes, 0, confNodes.length)
                .filter(StringUtils::isNotBlank)
                .map(Node::from)
                .forEach(nodes::add);

        return nodes;
    }

    public Set<Volume> getConfiguredVolumes() {
        Set<Volume> volumes = new HashSet<>();

        // 1. Add all configured volumes
        String[] confVolumes = configuredVolumes.split(",");
        Arrays.stream(confVolumes, 0, confVolumes.length)
                .map(Volume::from)
                .forEach(volumes::add);

        return volumes;
    }

    public Set<Volume> getAllVolumes() throws ManagementException {

        Set<Volume> volumes = getConfiguredVolumes();

        // 2. Add all previously discovered volumes
        JsonWrapper runtimeConfig = loadRuntimeSettings();
        if (!runtimeConfig.isEmpty()) {
            String discoveredVolumes = runtimeConfig.getValueForPathAsString("discovered-volumes");
            if (StringUtils.isNotBlank(discoveredVolumes)) {
                String[] discVolumes = discoveredVolumes.split(",");
                Arrays.stream(discVolumes, 0, discVolumes.length)
                        .map(String::trim)
                        .map(Volume::from)
                        .forEach(volumes::add);
            }
        }

        return volumes;
    }

    Set<Node> getRuntimeNodes(Map<Node, NodeStatus> nodesStatus) throws ManagementException {
        Set<Node> allNodes = new TreeSet<>(getAllNodes());

        nodesStatus.values()
                .forEach(status -> {
                    try {
                        status.getAllPeers().stream()
                                .filter(host -> !host.matches("localhost"))
                                .forEach(allNodes::add);
                    } catch (NodeStatusException e) {
                        // ignored here.
                    }
                });

        // store it if changed
        JsonWrapper runtimeConfig = loadRuntimeSettings();
        String savedNodes = "";
        if (!runtimeConfig.isEmpty()) {
            savedNodes = runtimeConfig.getValueForPathAsString("discovered-nodes");
        }
        String runtimeNodes = allNodes.stream().map(Node::getAddress).collect(Collectors.joining(","));
        if (!runtimeNodes.equals(savedNodes)) {
            runtimeConfig.setValueForPath("discovered-nodes", runtimeNodes);
            saveRuntimeSetting(runtimeConfig);
        }
        return allNodes;
    }

    Set<Volume> getRuntimeVolumes(Map<Node, NodeStatus> nodesStatus) throws ManagementException {
        Set<Volume> allVolumes = new TreeSet<>(getAllVolumes());

        nodesStatus.values()
                .forEach(status -> {
                    try {
                        allVolumes.addAll (status.getAllVolumes());
                    } catch (NodeStatusException e) {
                        // ignored here.
                    }
                });

        // store it if changed
        JsonWrapper runtimeConfig = loadRuntimeSettings();
        String savedVolumes = "";
        if (!runtimeConfig.isEmpty()) {
            savedVolumes = runtimeConfig.getValueForPathAsString("discovered-volumes");
        }
        String runtimeVolumes = allVolumes.stream()
                .map(Volume::getName)
                .collect(Collectors.joining(","));
        if (!runtimeVolumes.equals(savedVolumes)) {
            runtimeConfig.setValueForPath("discovered-volumes", runtimeVolumes);
            saveRuntimeSetting(runtimeConfig);
        }
        return allVolumes;
    }

    public void info(String s) {
        logger.info (s);
        messagingService.addLine(getMessageDate() + " - INFO: " + s);
    }

    public void error(String s) {
        logger.error (s);
        messagingService.addLine(getMessageDate() + " - ERROR: " + s);
    }

    public void error(Exception e) {
        logger.error (e, e);
        messagingService.addLine(getMessageDate() + " - ERROR: " + e.getMessage());
    }

    private String getMessageDate() {
        SimpleDateFormat format = formatLocal.get();
        return format.format(new Date());
    }

    public String getEnvironmentProperty (String property) {
        return env.getProperty(property);
    }

    public void saveRuntimeSetting(JsonWrapper settings) throws ManagementException {
        runtimeConfigLock.lock();
        try {
            FileUtils.writeFile(new File(configStoragePath + RUNTIME_CONFIG_JSON_PATH), settings.getFormattedValue());
        } catch (FileException e) {
            logger.error (e, e);
            throw new ManagementException(e);
        } finally {
            runtimeConfigLock.unlock();
        }
    }

    public JsonWrapper loadRuntimeSettings() throws ManagementException {
        runtimeConfigLock.lock();
        try {
            File statusFile = new File(configStoragePath + RUNTIME_CONFIG_JSON_PATH);
            if (!statusFile.exists()) {
                return new JsonWrapper("{}");
            }

            return new JsonWrapper(new String (FileUtils.read(statusFile)));
        } catch (FileException e) {
            logger.error (e, e);
            throw new ManagementException(e);
        } finally {
            runtimeConfigLock.unlock();
        }
    }

    public void updateSettingsAtomically (SettingsUpdater updater) throws ManagementException{
        runtimeConfigLock.lock();
        try {
            saveRuntimeSetting (updater.updateSettings(loadRuntimeSettings()));
        } finally {
            runtimeConfigLock.unlock();
        }
    }

    public void executeInLock(Action action) throws ActionException {
        try {
            statusUpdateLock.lock();

            action.execute();

        } finally {
            statusUpdateLock.unlock();
        }
    }

    public interface Action {

        void execute () throws ActionException;
    }

    public interface SettingsUpdater {

        JsonWrapper updateSettings(JsonWrapper original);

    }
}
