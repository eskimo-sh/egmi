package ch.niceideas.eskimo.egmi.problems.test;

import ch.niceideas.eskimo.egmi.gluster.GlusterRemoteManager;
import ch.niceideas.eskimo.egmi.model.SystemStatus;
import ch.niceideas.eskimo.egmi.problems.AbstractProblem;
import ch.niceideas.eskimo.egmi.problems.CommandContext;
import ch.niceideas.eskimo.egmi.problems.Problem;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestProblem extends AbstractProblem implements Problem {

    final int priority;
    final String type;
    final String id;
    final StringBuilder sb;

    @Override
    public String getProblemId() {
        return type + "-" + id;
    }

    @Override
    public String getProblemType() {
        return type;
    }

    @Override
    public boolean recognize(SystemStatus newStatus) {
        return true;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean solve(GlusterRemoteManager glusterRemoteManager, CommandContext context) {
        sb.append(getProblemId()).append("\n");
        return true;
    }
}
