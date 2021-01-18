package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.eskimo.egmi.gluster.command.AbstractGlusterSimpleOperation;
import ch.niceideas.eskimo.egmi.gluster.command.result.SimpleOperationResult;
import ch.niceideas.eskimo.egmi.management.GraphPartitionDetector;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import ch.niceideas.eskimo.egmi.model.NodeStatusException;
import lombok.NoArgsConstructor;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@NoArgsConstructor
public abstract class AbstractProblem implements Problem{

    private static final Logger logger = Logger.getLogger(AbstractProblem.class);

    protected static Set<String> getActiveNodes(Map<String, NodeStatus> nodesStatus) {
        return nodesStatus.keySet().stream()
                .filter(node -> nodesStatus.get(node) != null)
                .filter(node -> !nodesStatus.get(node).isPoolStatusError())
                .collect(Collectors.toSet());
    }

    protected static Set<String> getActiveConnectedNodes(Map<String, NodeStatus> nodesStatus) throws NodeStatusException {

        Set<String> activeNodes = getActiveNodes(nodesStatus);
        if (activeNodes.size() == 0) {
            return Collections.emptySet();
        }

        Map<String, GraphPartitionDetector.Node> nodeNetwork = GraphPartitionDetector.buildNodeGraph(activeNodes, nodesStatus);

        Map<String, Integer> counters = GraphPartitionDetector.buildPeerCounters(activeNodes, nodeNetwork);

        AtomicInteger currentCount = new AtomicInteger(Integer.MIN_VALUE);
        AtomicReference<String> host = new AtomicReference<>();
        counters.keySet().forEach( node -> {
                    if (counters.get(node) > currentCount.get()) {
                        currentCount.set(counters.get(node));
                        host.set(node);
                    }
                });

        Set<String> retSet = GraphPartitionDetector.buildPeerNetwork(nodeNetwork, host.get());
        retSet.retainAll(activeNodes);
        return retSet;
    }

    public static void executeSimpleOperation(AbstractGlusterSimpleOperation operation, CommandContext context, String host)
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
