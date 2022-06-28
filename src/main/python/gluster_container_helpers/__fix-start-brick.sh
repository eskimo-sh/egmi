#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
#  Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software,
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#


# Inject topology
. /etc/eskimo_topology.sh

VOLUME=$1
if [[ $VOLUME == "" ]]; then
   echo "Expecting Gluster VOLUME as first argument"
   exit 1
fi

NODE=$2
if [[ $NODE == "" ]]; then
   echo "Expecting Gluster NODE as second argument"
   exit 1
fi

echo "  __fix-start-brick.sh on volume $VOLUME for node $NODE"


echo "   + 1. confirm brick on this node is the node passed in argument and brick for volume exist"

# confirm node is current node
if [[ `/sbin/ifconfig | grep $NODE` == "" ]]; then
    echo "$NODE doesn't match any of the current node IP adresses"
    exit 1
fi

# confirm brick for volume exist
BRICK_PATH=$(/usr/sbin/gluster volume info $VOLUME | grep Brick  | grep $NODE | cut -d ":" -f 3 | xargs)
if [[ "$BRICK_PATH" == "" ]]; then
    echo "No brick found for volume $VOLUME on node $NODE"
    exit 2
fi


echo "   + 2. confirm brick is reported offline indeed"

if [[ `/usr/sbin/gluster volume status $VOLUME $NODE:$BRICK_PATH detail | grep Online | cut -d ":" -f 2 | xargs` == "Y" ]]; then
    echo "Brick $NODE:$BRICK_PATH is already reported online"
    exit 0
fi


echo "   + 3. see if the process for the brick is up and running"
if [[ `ps -efl | grep glusterfsd | grep $NODE | grep $VOLUME` != "" ]]; then

    echo "   + 4. if it is, kill it"
    PID=$(pgrep -f "$VOLUME.$NODE")
    kill -15 $PID
    sleep 2

fi


echo "   + 5. try gluster start volume again"
/usr/sbin/gluster volume start $VOLUME force
sleep 3


echo "   + 6. ensure process is properly restarted"
if [[ `/usr/sbin/gluster volume status $VOLUME $NODE:$BRICK_PATH detail | grep Online | cut -d ":" -f 2 | xargs` != "Y" ]]; then
    echo "Failed to force start brick $NODE:$BRICK_PATH"
    exit 3
fi


echo "success"