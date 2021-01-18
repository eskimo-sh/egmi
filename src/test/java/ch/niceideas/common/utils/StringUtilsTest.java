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

package ch.niceideas.common.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest  {

    private static final String SOURCE = "abc erd ohd the ad lazy fox jumps over ad my beautiful self again and is'nt that just sad nope ?";


    @Test
    public void testFirstLetterLowerCase() {
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("abcdf"));
        assertEquals("abcdf", StringUtils.firstLetterLowerCase("Abcdf"));

        assertEquals("a", StringUtils.firstLetterLowerCase("a"));
        assertEquals("a", StringUtils.firstLetterLowerCase("A"));

        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("aBCDE"));
        assertEquals("aBCDE", StringUtils.firstLetterLowerCase("ABCDE"));
    }

    @Test
    public void testFirstLetterUpperCase() {
        assertEquals("Abcdf", StringUtils.firstLetterUpperCase("abcdf"));
        assertEquals("Abcdf", StringUtils.firstLetterUpperCase("Abcdf"));

        assertEquals("A", StringUtils.firstLetterUpperCase("a"));
        assertEquals("A", StringUtils.firstLetterUpperCase("A"));

        assertEquals("ABCDE", StringUtils.firstLetterUpperCase("aBCDE"));
        assertEquals("ABCDE", StringUtils.firstLetterUpperCase("ABCDE"));
    }

    @Test
    public void testToBigDecimal() {
        assertEquals(new BigDecimal("0"), StringUtils.toBigDecimal("", new BigDecimal("0")));
        assertEquals(new BigDecimal("1"), StringUtils.toBigDecimal(" ", new BigDecimal("1")));
        assertEquals(new BigDecimal("2"), StringUtils.toBigDecimal(null, new BigDecimal("2")));

        assertEquals(new BigDecimal("100"), StringUtils.toBigDecimal("100", new BigDecimal("0")));
        assertEquals(new BigDecimal("200"), StringUtils.toBigDecimal("  200", new BigDecimal("0")));
        assertEquals(new BigDecimal("300"), StringUtils.toBigDecimal("300  ", new BigDecimal("0")));
    }

    @Test
    public void testPadRight() {
        assertEquals("a  ", StringUtils.padRight("a", 3));
        assertEquals("a    ", StringUtils.padRight("a", 5));
        assertEquals("a         ", StringUtils.padRight("a", 10));
        assertEquals("          ", StringUtils.padRight(null, 10));
    }

    @Test
    public void testPadLeft() {
        assertEquals("  a", StringUtils.padLeft("a", 3));
        assertEquals("    a", StringUtils.padLeft("a", 5));
        assertEquals("         a", StringUtils.padLeft("a", 10));
        assertEquals("          ", StringUtils.padLeft(null, 10));
    }

    @Test
    public void testUrlEncodeDecode() {
        String sourceString = "test encode\n with & some nutty?characters!";
        String encoded = StringUtils.urlEncode(sourceString);
        assertEquals("test+encode%0A+with+%26+some+nutty%3Fcharacters%21", encoded);
        assertEquals(sourceString, StringUtils.urlDecode(encoded));
    }

}
