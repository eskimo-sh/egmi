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

package ch.niceideas.common.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test class fo the ExceptionCollector class.
 */
public class CommonBusinessExceptionTest {


    /**
     * This tests the result of getLongMessage()
     */
    @Test
    public void testGetLongMessage () {

        CommonBusinessException a = new CommonBusinessException ("a");
        a.fillInStackTrace();

        CommonBusinessException b = new CommonBusinessException ("b");
        b.fillInStackTrace();
        a.addUnderlyingException(b);

        CommonBusinessException c = new CommonBusinessException ("c");

        c.fillInStackTrace();
        a.addUnderlyingException (c);

        CommonBusinessException c1 = new CommonBusinessException ("c1");
        c1.fillInStackTrace();
        c.addUnderlyingException (c1);

        CommonBusinessException c2 = new CommonBusinessException ("c2");
        c2.fillInStackTrace();
        c.addUnderlyingException (c2);

        CommonBusinessException c3 = new CommonBusinessException ("c3");
        c2.fillInStackTrace();
        c.addUnderlyingException (c3);

        String expectedResult = "a" + "\n" +
                "  b" + "\n" +
                "  c" + "\n" +
                "    c1" + "\n" +
                "    c2" + "\n" +
                "    c3";
        assertEquals (a.getCompleteMessage(), expectedResult);
    }

    /**
     * This tests the throwIfCauses() method.
     */
    @Test
    public void testThrowIfCauses() throws Exception {
        CommonBusinessException c = new CommonBusinessException();
        c.throwIfAny();
        c.throwIfAny();
        c.addUnderlyingException(new RuntimeException("test"));
        try {
            c.throwIfAny();
            fail();
        }
        catch (CommonBusinessException e) {
            // here that's fine
        }
        try {
            c.throwIfAny();
            fail();
        }
        catch (Exception e) {
            // here that's fine
        }

    }
}
