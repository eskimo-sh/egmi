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

package ch.niceideas.common.utils;

import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class defines additional string functions of config use
 */
public abstract class StringUtils {

    private static final Logger logger = Logger.getLogger(StringUtils.class);

    public static final String DEFAULT_ENCODING = "ISO-8859-1";

    private static final int LARGEST_FIELD_LENGTH = 100;

    private static final Pattern INTEGER_VALUE = Pattern.compile(" *[\\-+]? *[0-9]+(\\.(0)*)?");

    private StringUtils() {}

    public static boolean isIntegerValue(Object value) {
        if (value == null) {
            return false;
        }

        Matcher matcher = INTEGER_VALUE.matcher(value.toString());
        return matcher.matches();
    }

    /**
     * Convert a String into something more usable for URLs
     * 
     * @param source the source string to encode
     * @return the URL encoded version
     */
    public static String urlEncode(String source) {
        String retValue ;
        try {
            retValue = URLEncoder.encode(source, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.error(e, e);
            retValue = source;
        }
        return retValue;
    }

    /**
     * De-convert a String from something more usable for URLs
     * 
     * @param source the source string to encode
     * @return the URL encoded version
     */
    public static String urlDecode(String source) {
        String retValue;
        try {
            retValue = URLDecoder.decode(source, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.error(e, e);
            retValue = source;
        }
        return retValue;
    }

    /**
     * Put the first letter to lower case in the string given as argument and return the new string.
     *
     * @return Same as propertyName but with first letter lower case.
     */
    public static String firstLetterLowerCase(String original) {
        char[] charArray = original.toCharArray();
        charArray[0] = String.valueOf(charArray[0]).toLowerCase().charAt(0);
        return String.valueOf(charArray);
    }

    /**
     * Put the first letter to upper case in the string given as argument and return the new string.
     *
     * @return Same as propertyName but with first letter upper case.
     */
    public static String firstLetterUpperCase(String original) {
        char[] charArray = original.toCharArray();
        charArray[0] = String.valueOf(charArray[0]).toUpperCase().charAt(0);
        return String.valueOf(charArray);
    }

    /**
     * Conevrts the string given as argument in a BigDecimal value. If any error is encountered during the
     * conversion, defaultValue is returned as well.
     * 
     * @param stringValue The string to convert in a BigDecimal.
     * @param defautValue The default value to be returned insted if the conversion fails
     * @return The resulting BigDecimal value
     */
    public static BigDecimal toBigDecimal(String stringValue, BigDecimal defautValue) {
        if (isNotEmpty(stringValue)) {
            try {
                return new BigDecimal(stringValue.trim());
            } catch (NumberFormatException e) {
                logger.debug (e, e);
            }
        }
        return defautValue;
    }

    /**
     * <p>
     * Checks if a String is empty ("") or null.
     * </p>
     * 
     * <pre>
     * StringUtils.isEmpty(null)      = true
     * StringUtils.isEmpty(&quot;&quot;)        = true
     * StringUtils.isEmpty(&quot; &quot;)       = false
     * StringUtils.isEmpty(&quot;bob&quot;)     = false
     * StringUtils.isEmpty(&quot;  bob  &quot;) = false
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is empty or null
     */
    public static boolean isEmpty(String string) {
        return string == null || string.equals("");
    }

    /**
     * <p>
     * Checks if a String is whitespace, empty ("") or null.
     * </p>
     * 
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank(&quot;&quot;)        = true
     * StringUtils.isBlank(&quot; &quot;)       = true
     * StringUtils.isBlank(&quot;bob&quot;)     = false
     * StringUtils.isBlank(&quot;  bob  &quot;) = false
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is null, empty or whitespace
     */
    public static boolean isBlank(String string) {
        return isEmpty(string);
    }

    /**
     * <p>
     * Checks if a String is not empty ("") and not null.
     * </p>
     * 
     * <pre>
     * StringUtils.isNotEmpty(null)      = false
     * StringUtils.isNotEmpty(&quot;&quot;)        = false
     * StringUtils.isNotEmpty(&quot; &quot;)       = true
     * StringUtils.isNotEmpty(&quot;bob&quot;)     = true
     * StringUtils.isNotEmpty(&quot;  bob  &quot;) = true
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is not empty and not null
     */
    public static boolean isNotEmpty(String string) {
        return !isEmpty(string);
    }

    /**
     * <p>
     * Checks if a String is not empty (""), not null and not whitespace only.
     * </p>
     * 
     * <pre>
     * StringUtils.isNotBlank(null)      = false
     * StringUtils.isNotBlank(&quot;&quot;)        = false
     * StringUtils.isNotBlank(&quot; &quot;)       = false
     * StringUtils.isNotBlank(&quot;bob&quot;)     = true
     * StringUtils.isNotBlank(&quot;  bob  &quot;) = true
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is not empty and not null and not whitespace
     */
    public static boolean isNotBlank(String string) {
        return !isEmpty(string);
    }

    /**
     * Replace all occurences of a substring within a string with another string.
     *
     * @param inString String to examine
     * @param oldPattern String to replace
     * @param newPattern String to insert
     * @return a String with the replacements
     */
    public static String replace(String inString, String oldPattern, String newPattern) {
        if (inString == null || inString.length() == 0 || oldPattern == null || oldPattern.length() == 0
                || newPattern == null) {
            return inString;
        }
        StringBuilder sbuf = new StringBuilder();
        // output StringBuilder we'll build up
        int pos = 0; // our position in the old string
        int index = inString.indexOf(oldPattern);
        // the index of an occurrence we've found, or -1
        int patLen = oldPattern.length();
        while (index >= 0) {
            sbuf.append(inString, pos, index);
            sbuf.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }
        sbuf.append(inString.substring(pos));
        // remember to append any characters to the right of a match
        return sbuf.toString();
    }

}
