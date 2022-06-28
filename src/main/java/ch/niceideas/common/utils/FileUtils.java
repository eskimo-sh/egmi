/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import java.io.*;
import java.nio.file.Files;


/**
 * Various utility methods for File manipulation
 */
public class FileUtils {

    private static final Logger logger = Logger.getLogger(FileUtils.class);

    private FileUtils() {}

    public static void writeFile (File file, String content) throws FileException {

        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file))) {
            fileWriter.write(content);

        } catch (IOException e) {
            logger.error(e, e);
            throw new FileException(e.getMessage(), e);
        }
    }

    /**
     * Supports recursive deletion of directories
     * 
     * @param file the file to be deleted
     */
    public static void delete(File file) throws FileDeleteFailedException {
        delete (file, 0);
    }

    private static void delete(File file, int attempt) throws FileDeleteFailedException {

        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {

            // directory is empty, then delete it
            String[] fileContent = file.list();
            if (fileContent == null || fileContent.length == 0) {

                if (file.delete()) {
                    //logger.debug("Directory is deleted : " + file.getAbsolutePath());
                } else {
                    throw new FileDeleteFailedException ("Could not delete directory " + file.getAbsolutePath());
                }
                //logger.debug ("Directory is deleted : " + file.getAbsolutePath());

            } else {

                for (File underFile : file.listFiles()) {

                    // recursive delete
                    delete(underFile);
                }

                // check the directory again, if empty then delete it
                fileContent = file.list();
                if (fileContent != null && fileContent.length > 0 && attempt >= 5) {
                    throw new FileDeleteFailedException ("Could not delete all files from directory " + file.getAbsolutePath());
                }

                // try delete once more
                delete (file, attempt + 1);
            }

        } else {
            // if file, then delete it
            if (file.delete()) {
                //logger.debug("File is deleted : " + file.getAbsolutePath());
            } else {
                throw new FileDeleteFailedException ( "Could not delete file " + file.getAbsolutePath());
            }            
        }
    }
    
    public static class FileDeleteFailedException extends Exception {
        
        private static final long serialVersionUID = 693506294359857541L;

        FileDeleteFailedException(String message) {
            super(message);
        }
        
    }
    
    /**
     * Copy the content of the source file to the destination file
     * 
     * @param src the source file
     * @param dst the destination file
     * @throws IOException if anything goes wrong
     */
    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        StreamUtils.copyThenClose(in, out);
    }

    /**
     * Closes InputStream and/or OutputStream. It makes sure that both streams tried to be closed, even first throws
     * an exception.
     * 
     * @throws IOException if stream (is not null and) cannot be closed.
     * 
     */
    public static void close(InputStream iStream, OutputStream oStream) {
        StreamUtils.close(iStream);
        StreamUtils.close(oStream);
    }

    public static void write(File file, String content) throws FileException {

        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file))) {
            fileWriter.write(content);

        } catch (IOException e) {
            logger.error(e, e);
            throw new FileException(e.getMessage(), e);
        }
    }

    public static byte[] read(File file) throws FileException {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            logger.error(e, e);
            throw new FileException(e.getMessage(), e);
        }
    }

}
