package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.common.http.HttpClientException;
import ch.niceideas.eskimo.egmi.gluster.command.AbstractGlusterSimpleOperation;
import ch.niceideas.eskimo.egmi.gluster.command.result.SimpleOperationResult;
import ch.niceideas.eskimo.egmi.model.NodeStatus;
import lombok.NoArgsConstructor;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
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
