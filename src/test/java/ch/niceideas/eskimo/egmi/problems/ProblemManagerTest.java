package ch.niceideas.eskimo.egmi.problems;

import ch.niceideas.eskimo.egmi.management.ManagementService;
import ch.niceideas.eskimo.egmi.problems.test.TestProblem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProblemManagerTest {

    private final ProblemManager problemManager = new ProblemManager();

    @Test
    public void testResolutionIteration_nominal() {

        problemManager.setManagementService(new ManagementService() {
            @Override
            public boolean isMaster() {
                return true;
            }
        });

        StringBuilder sb = new StringBuilder();

        problemManager.addProblem(new TestProblem(1, "test1", "test1_1", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_1", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_1", sb));

        problemManager.addProblem(new TestProblem(1, "test1", "test1_2", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_2", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_2", sb));

        problemManager.addProblem(new TestProblem(1, "test1", "test1_3", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_3", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_3", sb));

        problemManager.resolutionIteration(null);

        assertEquals("test1-test1_3\n" +
                "test1-test1_2\n" +
                "test1-test1_1\n" +
                "test2-test2_1\n" +
                "test2-test2_3\n" +
                "test2-test2_2\n" +
                "test3-test3_1\n" +
                "test3-test3_2\n" +
                "test3-test3_3\n", sb.toString());

        sb.delete(0, sb.length());

        problemManager.resolutionIteration(null);

        // all of them were solved at first iteration
        assertEquals("", sb.toString());
    }

    @Test
    public void testResolutionIteration_eliminateDuplicates() {

        problemManager.setManagementService(new ManagementService() {
            @Override
            public boolean isMaster() {
                return true;
            }
        });

        StringBuilder sb = new StringBuilder();

        problemManager.addProblem(new TestProblem(1, "test1", "test1_1", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_1", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_1", sb));

        problemManager.addProblem(new TestProblem(1, "test1", "test1_1", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_1", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_1", sb));

        problemManager.addProblem(new TestProblem(1, "test1", "test1_1", sb));
        problemManager.addProblem(new TestProblem(2, "test2", "test2_1", sb));
        problemManager.addProblem(new TestProblem(3, "test3", "test3_1", sb));

        problemManager.resolutionIteration(null);

        assertEquals("test1-test1_1\n" +
                "test2-test2_1\n" +
                "test3-test3_1\n", sb.toString());
    }
}
