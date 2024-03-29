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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

public class FileUtilsTest {

    @Test
    public void testDeleteFile() throws Exception {

        File fileToDelete = File.createTempFile("test", "delete");
        FileUtils.write(fileToDelete, "content");

        assertTrue (fileToDelete.exists());
        FileUtils.delete(fileToDelete);
        assertFalse (fileToDelete.exists());

        assertTrue (fileToDelete.mkdirs());
        assertTrue(fileToDelete.exists());

        File ftd1 = File.createTempFile("test", "delete", fileToDelete);
        FileUtils.write(ftd1, "content");
        assertTrue(ftd1.exists());

        File ftd2 = File.createTempFile("test", "delete", fileToDelete);
        FileUtils.write(ftd2, "content");
        assertTrue(ftd2.exists());

        FileUtils.delete(fileToDelete);

        assertFalse(fileToDelete.exists());
        assertFalse(ftd1.exists());
        assertFalse(ftd2.exists());
    }

    @Test
    public void testCopy() throws Exception {

        File file1 = File.createTempFile("test", "source");
        FileUtils.write(file1, "content");

        File file2 = File.createTempFile("test", "dest");
        FileUtils.copy(file1, file2);

        assertTrue (file2.exists());
        assertEquals ("content", new String (FileUtils.read(file2), Charset.defaultCharset()));

    }
}
