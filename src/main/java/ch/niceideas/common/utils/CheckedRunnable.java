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

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.common.exceptions.WrappedRTException;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;

@FunctionalInterface
public interface CheckedRunnable<E extends CommonBusinessException> {

    Logger logger = Logger.getLogger(CheckedRunnable.class);

    void run() throws E;

    static <T, E extends CommonBusinessException> void unwrap (CheckedRunnable<E> runnable, Class<? extends E> exceptionClass) throws E {
        try {
            runnable.run();
        } catch (WrappedRTException e) {
            unwrapException(exceptionClass, e);
        }
    }

    static <E extends CommonBusinessException> void unwrapException(Class<? extends E> exceptionClass, WrappedRTException e) throws E {
        if (exceptionClass.isInstance(e.getCause())) {
            try {

                @SuppressWarnings("unchecked")
                Class<E> clazz = ((Class<E>) e.getCause().getClass());

                try {
                    throw clazz.getDeclaredConstructor(String.class, Throwable.class).newInstance(e.getMessage(), e);
                } catch (NoSuchMethodException sub) {

                    try {
                        throw clazz.getDeclaredConstructor(Throwable.class).newInstance(e);
                    } catch (NoSuchMethodException sub2) {

                        try {
                            throw clazz.getDeclaredConstructor().newInstance();
                        } catch (NoSuchMethodException sub3) {
                            throw new IllegalStateException(sub3);
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException sub) {
                throw new IllegalStateException(sub);
            }

        } else {
            logger.warn (e.getCause().getClass() + " is not assignable to " + exceptionClass);
            throw new IllegalStateException(e);
        }
    }
}