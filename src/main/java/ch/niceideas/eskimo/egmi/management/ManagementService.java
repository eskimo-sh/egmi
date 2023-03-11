
package ch.niceideas.eskimo.egmi.management;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterVolumeStatusResult;
import ch.niceideas.eskimo.egmi.model.*;
import ch.niceideas.eskimo.egmi.problems.*;
import ch.niceideas.eskimo.egmi.zookeeper.ZookeeperService;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
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
            List<JSONObject> nodeInfos = buildNodeInfo(nodesStatus, allNodes);

            SystemStatus newStatus = getSystemStatus(InetAddress.getLocalHost().toString(), nodesStatus, allNodes, allVolumes, nodeInfos);

            // 3. Detection connection graph partitioning
            GraphPartitionDetector.detectGraphPartitioning (problemManager, allNodes, nodeInfos, nodesStatus);

            // 4. Detect peer connection inconsistencies
            detectConnectionInconsistencies (problemManager, allNodes, nodesStatus, nodeInfos);

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

    SystemStatus getSystemStatus(String hostName, Map<Node, NodeStatus> nodesStatus, Set<Node> allNodes, Set<Volume> allVolumes, List<JSONObject> nodeInfos) throws NodeStatusException {
        SystemStatus newStatus = new SystemStatus("{\"hostname\" : \"" + hostName + "\"}");

        // 1. Build Node status
        newStatus.getJSONObject().put("nodes", new JSONArray(nodeInfos));

        // 2. Build Volume status
        newStatus.getJSONObject().put("volumes", new JSONArray(buildVolumeInfo(nodesStatus, allNodes, allVolumes, nodeInfos)));
        return newStatus;
    }

    private void detectConnectionInconsistencies(ProblemManager problemManager, Set<Node> allNodes, Map<Node, NodeStatus> nodesStatus, List<JSONObject> nodeInfos) {

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
                                if (flagNodeInconsistent(problemManager, nodeInfos, node, other)) {
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

    private boolean flagNodeInconsistent(ProblemManager problemManager, List<JSONObject> nodeInfos, Node node, Node other) {
        // flag node inconsistent
        for (JSONObject nodeInfo : nodeInfos) {
            if (node.matches (nodeInfo.getString("host"))) {
                String prevStatus = nodeInfo.getString("status");
                if (StringUtils.isBlank(prevStatus) || !prevStatus.equals("KO")) { // don't overwrite KO node
                    nodeInfo.put("status", "INCONSISTENT");
                    problemManager.addProblem(new NodeInconsistent(new Date(), node, other));
                    return true;
                }
            }
        }
        return false;
    }

    private List<JSONObject> buildVolumeInfo(Map<Node, NodeStatus> nodesStatus,
                                             Set<Node> allNodes, Set<Volume> allVolumes, List<JSONObject> nodeInfos)
            throws NodeStatusException {

        int targetNbrBricks = getTargetNumberOfBricks();
        int targetNbrReplicas = getTargetNumberOfReplicas();
        int targetNbrShards = targetNbrBricks / targetNbrReplicas;

        List<JSONObject> volumesInfo = new ArrayList<>();
        for (Volume volume : allVolumes) {
            JSONObject volumeSystemStatus = new JSONObject();
            volumeSystemStatus.put("volume", volume.getName());

            Set<String> errors = new HashSet<>();

            // get volume information from all nodes

            Map<String, String> options = new HashMap<>();

            List<Pair<BrickId, JSONObject>> bricksInfo = new ArrayList<>();

            for (Node node : allNodes) {

                NodeStatus nodeStatus = nodesStatus.get(node);
                if (nodeStatus != null) {

                    VolumeInformation nodeVolumeInfo = nodeStatus.getVolumeInformation(volume);

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

                    String prevType = volumeSystemStatus.has("type") ? volumeSystemStatus.getString("type") : null;
                    String prevOwner = volumeSystemStatus.has("owner") ? volumeSystemStatus.getString("owner") : null;

                    String prevNbShards = volumeSystemStatus.has("nb_shards") ? volumeSystemStatus.getString("nb_shards") : null;
                    String prevNbReplicas = volumeSystemStatus.has("nb_replicas") ? volumeSystemStatus.getString("nb_replicas") : null;
                    String prevNbBricks = volumeSystemStatus.has("nb_bricks") ? volumeSystemStatus.getString("nb_bricks") : null;

                    //private void setInVolumeStatus(JSONObject volumeSystemStatus, String attribute, String value, String previous, boolean append, Set<String> errors, String errorTag) {
                    setInVolumeStatus(volumeSystemStatus, "type", type, prevType, true, errors, "TYPES");

                    setInVolumeStatus(volumeSystemStatus, "owner", owner, prevOwner, true, errors, "OWNER");

                    setInVolumeStatus(volumeSystemStatus, "nb_shards", nbShards, prevNbShards, false, errors, "NB. SHARDS");

                    setInVolumeStatus(volumeSystemStatus, "nb_replicas", nbReplicas, prevNbReplicas, false, errors, "NB. REPL.");

                    setInVolumeStatus(volumeSystemStatus, "nb_bricks", nbBricks, prevNbBricks, false, errors, "NB. BRICKS");

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
                    Map<BrickId, BrickInformation> nodeBricksInfo = nodeStatus.getVolumeBricksInformation(volume);
                    List<BrickId> brickIdList = new ArrayList<>(nodeBricksInfo.keySet());
                    brickIdList.sort(new BrickIdNumberComparator (nodeBricksInfo));

                    for (BrickId brickId : brickIdList) {

                        BrickInformation nodeBrickInfo = nodeBricksInfo.get(brickId);

                        JSONObject brickInfo = null;
                        for (Pair<BrickId, JSONObject> brickInfoWrapper : bricksInfo) {
                            if (brickInfoWrapper.getKey().equals(brickId)) {
                                brickInfo = brickInfoWrapper.getValue();
                                break;
                            }
                        }
                        if (brickInfo == null) {
                            brickInfo = new JSONObject();
                            bricksInfo.add (new Pair<>(brickId, brickInfo));
                        }

                        String prevId = brickInfo.has("id") ? brickInfo.getString("id") : null;

                        //private void setInBrickInfo(JSONObject brickInfo, String value, String previous, Set<String> errors, String errorTag) {
                        setInBrickInfo(brickInfo, "id", brickId.toString(), prevId, errors, "ID");

                        Integer effNumberInt = nodeBrickInfo != null ? nodeBrickInfo.getNumber() : null;
                        String effNumber = "?";
                        if (effNumberInt != null) {
                            effNumber = effNumberInt.toString();
                        }
                        String prevNumber = brickInfo.has("number") ? brickInfo.getString("number") : null;

                        setInBrickInfo(brickInfo, "number", effNumber, prevNumber, errors, "NBR");

                        String effNode = brickId.getNode().getAddress();
                        String prevNode = brickInfo.has("node") ? brickInfo.getString("node") : null;

                        setInBrickInfo(brickInfo, "node", effNode, prevNode, errors, "NODE");

                        String effPath = brickId.getPath();
                        String prevPath = brickInfo.has("path") ? brickInfo.getString("path") : null;

                        setInBrickInfo(brickInfo, "path", effPath, prevPath, errors, "PATH");

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
                            String prevStatus = brickInfo.has("status") ? brickInfo.getString("status") : null;

                            setInBrickInfo(brickInfo, "status", effStatus, prevStatus, errors, "STATUS");

                            String effDevice = nodeBrickInfo.getDevice();
                            String prevDevice = brickInfo.has("device") ? brickInfo.getString("device") : null;

                            setInBrickInfo(brickInfo, "device", effDevice, prevDevice, errors, "DEV");

                            String effFree = nodeBrickInfo.getFree();
                            brickInfo.put("free", effFree);

                            String effTot = nodeBrickInfo.getTotal();
                            brickInfo.put("tot", effTot);
                        }
                    }
                }
            }

            // Post status building consistency checks

            // If not type was found, the volume is likely not configured at all
            if (!volumeSystemStatus.has("type")) {
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

                            // FIXME create problem
                            problemManager.addProblem (new WrongOption(new Date(), volume, optionToTurnOff, optionValue, "off"));
                            errors.add(volume + " WRONG OPTION " + optionToTurnOff + "/" + optionValue);

                        }
                    }
                }
            }

            // Check if a brick could not be build at all
            for (Pair<BrickId, JSONObject> brickInfo : bricksInfo) {
                JSONObject brickInfoObj = brickInfo.getValue();
                String status = brickInfoObj.has("status") ? brickInfoObj.getString("status") : null;
                if (StringUtils.isBlank(status)) {
                    String nodeAddress = brickInfoObj.getString("node");
                    if (StringUtils.isNotBlank(nodeAddress)) {
                        Node node = Node.from(nodeAddress);
                        // find nodeInfo
                        for (JSONObject nodeInfo : nodeInfos) {
                            String nodeInfoNode = nodeInfo.getString("host");
                            if (node.matches(nodeInfoNode)) {
                                String nodeStatus = nodeInfo.has ("status") ? nodeInfo.getString("status") : null;
                                if (StringUtils.isBlank(nodeStatus) || nodeStatus.equals("KO")) {
                                    errors.add(node + " DOWN");
                                    problemManager.addProblem (new NodeDown(new Date(), volume, node));
                                    problemManager.addProblem (new NodeDownRemoval(new Date(), node));
                                }
                            }
                        }
                    }
                }
            }

            volumeSystemStatus.put("status", errors.size() == 0 ? "OK" : String.join(" / ", errors));

            volumeSystemStatus.put("bricks", new JSONArray(bricksInfo.stream().map(Pair::getValue).collect(Collectors.toList())));

            volumeSystemStatus.put("options", new JSONObject(options));

            volumesInfo.add (volumeSystemStatus);
        }
        return volumesInfo;
    }

    private void setInBrickInfo(JSONObject brickInfo, String attribute, String value, String previous, Set<String> errors, String errorTag) {
        if (StringUtils.isBlank(previous)) {
            brickInfo.put(attribute, value);
        } else if (!previous.equals(value)) {
            errors.add("DIFB. " + errorTag);
            notifyInconsistency(" - got brickId " + value + " while previous node had " + previous);
        }
    }

    private void setInVolumeStatus(JSONObject volumeSystemStatus, String attribute, String value, String previous, boolean append, Set<String> errors, String errorTag) {
        if (StringUtils.isNotBlank(value)) {
            if (StringUtils.isBlank(previous)) {
                volumeSystemStatus.put(attribute, value);
            } else if (!previous.equals(value)) {
                errors.add("DIF " + errorTag);
                if (append) {
                    volumeSystemStatus.put(attribute, previous + "," + value);
                } else {
                    notifyInconsistency(" - for volume " + volumeSystemStatus.get("volume")
                            + " - got " + attribute + "  " + value + " while previous node had " + previous);
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

    List<JSONObject> buildNodeInfo(Map<Node, NodeStatus> nodesStatus, Set<Node> allNodes) throws NodeStatusException {

        List<JSONObject> nodesInfo = new ArrayList<>();
        for (Node node : allNodes) {
            JSONObject nodeSystemStatus = new JSONObject();
            nodeSystemStatus.put("host", node.getAddress());

            NodeStatus nodeStatus = nodesStatus.get(node);
            if (nodeStatus == null) {
                nodeSystemStatus.put("status", "KO");
            } else {

                if (nodeStatus.isPoolStatusError()) {
                    nodeSystemStatus.put("status", "KO");
                } else {
                    nodeSystemStatus.put("status", "OK");
                    Map<String, Object> nodeInfo = nodeStatus.getNodeInformation(node);

                    @SuppressWarnings("unchecked")
                    Set<String> nodeVolumes = (Set<String>) nodeInfo.get("volumes");
                    if (nodeVolumes != null) {
                        nodeSystemStatus.put("volumes", String.join(", ", nodeVolumes));
                    } else {
                        nodeSystemStatus.put("volumes", "?");
                    }

                    Integer nodeBrickCount =  (Integer) nodeInfo.get("brick_count");
                    nodeSystemStatus.put("nbr_bricks", Objects.requireNonNullElse(nodeBrickCount, "?"));
                }
            }

            nodesInfo.add (nodeSystemStatus);
        }
        return nodesInfo;
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
