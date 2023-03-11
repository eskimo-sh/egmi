package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.eskimo.egmi.gluster.command.AbstractGlusterSimpleOperation;
import ch.niceideas.eskimo.egmi.gluster.command.GlusterPoolList;
import ch.niceideas.eskimo.egmi.gluster.command.result.GlusterPoolListResult;
import ch.niceideas.eskimo.egmi.gluster.command.result.SimpleOperationResult;
import ch.niceideas.eskimo.egmi.management.GraphPartitionDetector;
import ch.niceideas.eskimo.egmi.model.Node;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.NodeStatusException;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@NoArgsConstructor
@EqualsAndHashCode
public abstract class AbstractProblem implements Problem{

    private static final Logger logger = Logger.getLogger(AbstractProblem.class);

    public static boolean checkHostInPeerPool(CommandContext context, Node host, Node peer) throws HttpClientException, IOException, ResolutionStopException {
        int attempt;
        for (attempt = 0; attempt < 5; attempt++) {
            context.info("        + checking pool on  " + peer + " - attempt " + attempt);
            GlusterPoolList poolList = new GlusterPoolList(context.getHttpClient());
            GlusterPoolListResult poolListResult = poolList.execute(peer, context);
            if (!poolListResult.isSuccess()) {
                context.error("      ! Failed checking pool on  " + peer + " - " + poolListResult.getError());
                throw new ResolutionStopException("! Failed checking pool on  " + peer + " - " + poolListResult.getError());
            }
            if (poolListResult.contains (host)) {
                context.info("        + found  " + host + " in pool on " + peer);
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logger.debug (e, e);
            }
        }
        if (attempt == 5) {
            context.error ("      ! Failed to confirm peer addition in 5 attempts.");
            throw new ResolutionStopException("! Failed to confirm peer addition in 5 attempts.");
        }
        return false;
    }

    protected static Set<Node> getActiveNodes(Map<Node, NodeStatus> nodesStatus) {
        return nodesStatus.keySet().stream()
                .filter(node -> nodesStatus.get(node) != null)
                .filter(node -> !nodesStatus.get(node).isPoolStatusError())
                .collect(Collectors.toSet());
    }

    protected static Optional<Node> getFirstNode(Set<Node> nodes) {
        return nodes.stream()
                .sorted()
                .findFirst();

    }

    protected static Set<Node> getActiveConnectedNodes(Map<Node, NodeStatus> nodesStatus) throws NodeStatusException {

        Set<Node> activeNodes = getActiveNodes(nodesStatus);
        if (activeNodes.size() == 0) {
            return Collections.emptySet();
        }

        Map<Node, GraphPartitionDetector.GraphNode> nodeNetwork = GraphPartitionDetector.buildNodeGraph(activeNodes, nodesStatus);

        Map<Node, Integer> counters = GraphPartitionDetector.buildPeerTimesVolumeCounters(activeNodes, nodeNetwork, nodesStatus);

        AtomicInteger currentCount = new AtomicInteger(Integer.MIN_VALUE);
        AtomicReference<Node> host = new AtomicReference<>();
        counters.keySet().forEach( node -> {
                    if (counters.get(node) > currentCount.get()) {
                        currentCount.set(counters.get(node));
                        host.set(node);
                    }
                });

        Set<Node> retSet = GraphPartitionDetector.buildPeerNetwork(nodeNetwork, host.get());
        retSet.retainAll(activeNodes);
        return retSet;
    }

    public static void executeSimpleOperation(AbstractGlusterSimpleOperation operation, CommandContext context, Node host)
            throws ResolutionStopException {
        try {
            SimpleOperationResult result = operation.execute(host, context);
            if (!result.isSuccess()) {
                context.error("      !! " + operation.getClass().getSimpleName()  + " failed. See backend logs for details");
                logger.error(result.getError());
                throw new ResolutionStopException(result.getError());
            }
        } catch (HttpClientException e) {
            logger.debug (e, e);
            logger.error (e.getCompleteMessage());
            throw new ResolutionStopException(e.getCompleteMessage(), e);

        } catch (IOException e) {
            logger.debug (e, e);
            logger.error (e.getMessage());
            throw new ResolutionStopException(e);
        }
    }

    public int compareTo(Problem o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return Integer.compare(this.getPriority(), o.getPriority());
    }

}
