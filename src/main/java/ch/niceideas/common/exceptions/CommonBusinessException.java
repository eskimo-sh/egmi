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


package ch.niceideas.common.exceptions;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>
 * The design of this class as well as in general the ideas expressed here and the original implementation of these
 * ideas are the work of Mr. Thomas Beck. <br />
 * I would like to take this opportunity to thank him for the privilege and the great pleasure it has been to work
 * during those years with the very best software engineer I have ever met in my career.
 * </b>
 */
public class CommonBusinessException extends Exception {

    private static final long serialVersionUID = -8017767618562184690L;

    private List<Throwable> underlyingExceptions = null;

    public CommonBusinessException() {}

    public CommonBusinessException(String message) {
        super(message);
    }

    public CommonBusinessException(String message, Throwable under) {
        super(message);
        this.addUnderlyingException(under);
    }

    public CommonBusinessException(Throwable under) {
        super();
        this.addUnderlyingException(under);
    }

    public void addUnderlyingException(Throwable under) {
        if (under != null) {
            if (underlyingExceptions == null) {
                underlyingExceptions = new ArrayList<>(1);
            }
            underlyingExceptions.add(under);
        }
    }

    public Throwable[] getUnderlyingExceptions() {
        if (underlyingExceptions == null) {
            return new Throwable[0];
        }
        Throwable[] array = new Throwable[underlyingExceptions.size()];
        return underlyingExceptions.toArray(array);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Throwable initCause(Throwable cause) {
        return super.initCause(cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Throwable getCause() {
        if (underlyingExceptions == null) {
            return null;
        }
        return underlyingExceptions.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printStackTrace() {
        System.err.println(getCompleteMessage());
        super.printStackTrace();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printStackTrace(PrintStream s) {
        s.println(getCompleteMessage());
        super.printStackTrace(s);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printStackTrace(PrintWriter s) {
        s.println(getCompleteMessage());
        super.printStackTrace(s);
    }

    public String getCompleteMessage() {
        StringBuilder messageBuilder = new StringBuilder();
        String message = this.getMessage();
        if (message == null) {
            message = this.getClass().getName();
        }
        messageBuilder.append(message);
        addUnderlyingExceptions(messageBuilder, this, 1);
        return messageBuilder.toString();
    }

    private void addUnderlyingExceptions (StringBuilder messageBuilder, Throwable parent, int level) {

        if (parent.getCause() == null) {
            return;
        }

        if (parent instanceof CommonBusinessException) {
            Throwable[] myUnderlyingExceptions = ((CommonBusinessException) parent).getUnderlyingExceptions();
            for (Throwable under : myUnderlyingExceptions) {
                appendMessage(messageBuilder, level, under);

                // recursive call on each cause
                addUnderlyingExceptions(messageBuilder, under, level + 1);

            }
        } else {

            appendMessage(messageBuilder, level, parent.getCause());

            addUnderlyingExceptions(messageBuilder, parent.getCause(), level + 1);
        }
    }

    private void appendMessage(StringBuilder messageBuilder, int level, Throwable under) {
        String message = under.getMessage();
        if (message == null) {
            message = under.getClass().getName();
        }
        messageBuilder.append("\n");
        messageBuilder.append("  ".repeat(Math.max(0, level)));
        messageBuilder.append(message);
    }

    @SuppressWarnings("unchecked")
    public <T extends CommonBusinessException> void throwIfAny() throws T {
        if (underlyingExceptions != null && underlyingExceptions.size() > 0) {
            throw (T) this;
        }
    }

    public void resetUnderlyingExceptions() {
        if (underlyingExceptions != null && underlyingExceptions.size() > 0) {
            underlyingExceptions.clear();
        }
    }
}
