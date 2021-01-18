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

import java.util.Collection;


/**
 * An instance of this class is thrown when a Value Object
 * contains an invalid value. It parameters can then be used
 * with {@link java.text.MessageFormat MessageFormat} to get
 * right message. The message this exceptions contains is not
 * the text but the key to get the right text from it.
 *
 * @author Kehrli Jerome
 * @version
 **/
public class InvalidValueException extends CommonRTException {

    private static final long serialVersionUID = 7754945735181373410L;

    private Object[] parameters = new Object[0];

    /**
     * Constructor with a message handler and a list of parameters
     *
     * @param messageHandler Handler to lookup the right message
     **/
    public InvalidValueException(String messageHandler) {
        super (messageHandler);
    }

    /**
     * Constructor with a message handler and a list of parameters
     *
     * @param messageHandler Handler to lookup the right message
     * @param parameters One Parameter, array of parameters or a Collection
     *      of parameters or null
     **/
    @SuppressWarnings("unchecked")
    public InvalidValueException  (String messageHandler, Object parameters) {
        super (messageHandler);

        if (parameters != null) {
            if (parameters instanceof Collection) {
                this.parameters = ((Collection<Object>) parameters).toArray (new Object[0]);

            } else {

                if (parameters instanceof Object[]) {
                    this.parameters = (Object[]) parameters;
                } else {
                    this.parameters = new Object[] {parameters};
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Methods
    // -------------------------------------------------------------------------

    /**
     * Returns the array of parameters coming along
     *
     * @return Array of parameters which are always defined but can be empty
     **/
    public Object[] getParameters() {
        return parameters;
    }

    /**
     * Describes the instance and its content for debugging purpose.
     * <p />
     * 
     * {@inheritDoc}
     **/
    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * Determines if the given instance is the same as this instance
     * based on its content. This means that it has to be of the same
     * class ( or subclass ) and it has to have the same content.
     * <p />
     * 
     * {@inheritDoc}
     *
     * @return Returns the equals value from the super class
     **/
    @Override
    public boolean equals (Object test) {
        return super.equals (test);
    }

    /**
     * Returns the hashcode of this instance.
     * <p />
     * 
     * {@inheritDoc}
     *
     * @return Hashcode of the super class
     **/
    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
