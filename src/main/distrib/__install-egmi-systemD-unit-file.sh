#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
#  Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

# This script takes care of performing sanity checks to ensure SystemD will be able to start EGMI and setup all
# the environment for this, including installing the EGMI SystemD Unit Configuration file.

function usage() {
    echo "Usage:"
    echo "    -h  Display this help message."
    echo "    -s  Skip everything related to SystemD"
    echo "    -f  assume 'y' answer to all questions'"
}

# Parse options to the install script
while getopts ":hfs" opt; do
    case ${opt} in
        h )
            usage
            exit 0
        ;;
        f )
            export FORCE=force
        ;;
        s )
            export SKIP_SYSTEM_D=skip
        ;;
        : )
            break
        ;;
        \? )
           echo "Invalid Option: -$OPTARG" 1>&2
           exit 1
         ;;
    esac
done

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

set -e

# Find out about script path
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# KK. First thing, find the egmi.service SystemD unit file (in utils sub-folder)
SYSTEM_D_FILE=$SCRIPT_DIR/../../utils/egmi.service
if [[ ! -f $SYSTEM_D_FILE ]]; then
    echo "Can't find find file $SYSTEM_D_FILE"
    exit 2
fi

if [[ $SKIP_SYSTEM_D != "skip" ]]; then
    # Locate SystemD units configuration folder
    if [[ -d /lib/systemd/system/ ]]; then
        export systemd_units_dir=/lib/systemd/system/
    elif [[ -d /usr/lib/systemd/system/ ]]; then
        export systemd_units_dir=/usr/lib/systemd/system/
    else
        echo "Couldn't find systemd unit files directory"
        exit 3
    fi

    cp $SYSTEM_D_FILE /tmp/egmi.service

    # REPLACE EGMI_PATH
    escaped_path=$(echo "$SCRIPT_DIR/../.." | sed 's/\//\\\//g')
    sed -i -E "s/\{EGMI_PATH\}/$escaped_path/g" /tmp/egmi.service
fi


echo " - Installing EGMI management scripts to /usr/local/sbin/"
cp $SCRIPT_DIR/../../utils/gluster_container_helpers/* /usr/local/sbin/


# Sanity checks:

# Ensure Java 11 in path (check java version)
echo " - Ensuring Java is in path"
set +e
export PATH=$JAVA_HOME/bin:$PATH
java_version=$(java -version)
if [[ $? != 0 ]]; then
    echo "Could not find any java executable in PATH."
    echo "EGMI needs to have the JDK 11 java executable in path or the JAVA_HOME env var properly defined"
    echo "Please re-launch this script after defining JAVA_HOME in the system profile or bashrc or adding the java executable in PATH"
    exit 5
fi
if [[ $(echo $java_version | grep version | grep "11") == "" ]]; then
    echo "The java version in path of JAVA_HOME is $(echo \"$java_version\" | grep version)"
    echo "EGMI needs JDK 11 or greater to run"
    if [[ $FORCE != "force" ]]; then
        while true; do
            read -p "Please confirm your JAVA version is 11 or greater ? (y/n)" yn
            case $yn in
                [Yy]* ) break;;
                [Nn]* ) exit;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi

set -e

# Handle capsh usage or installation
install_capsh(){

    echo " - checking whether gcc is installed"
    if [[ $(which gcc 2>/dev/null) == "" ]]; then
        echo "!!! capsh building needs gcc installed (e.g. yum install gcc) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install gcc and restart this script"
        exit 4
    fi

    echo " - checking whether git is installed"
    if [[ $(which git 2>/dev/null) == "" ]]; then
        echo "!!! capsh building needs git installed (e.g. yum install git) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install git and restart this script"
        exit 4
    fi

    echo " - checking whether libc static library is available"
    if [[ $(find / -name "libc.*a" 2>/dev/null) == "" ]]; then
        echo "!!! capsh building needs static libc installed (e.g. yum install glib-static) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install glibc static library and restart this script"
        exit 5
    fi

    rm -Rf /tmp/build_capsh
    mkdir -p /tmp/build_capsh
    cd /tmp/build_capsh

    echo " - Git cloning capsh"
    git clone git://git.kernel.org/pub/scm/linux/kernel/git/morgan/libcap.git

    cd libcap/

    echo " - Building capsh"
    make

    echo " - Installing capsh"
    cp ./progs/capsh $SCRIPT_DIR/capsh

    rm -Rf /tmp/build_capsh
}

if [[ $SKIP_SYSTEM_D != "skip" ]]; then
    if [[ ! -f $SCRIPT_DIR/capsh ]]; then
        # Find out about capsh possibilities
        if [[ $(which capsh 2>/dev/null) == "" ]]; then
            export CAPSH_NOT_FOUND=1
        else
            export CAPSH_NOT_FOUND=0

            if [[ $(capsh --help | grep 'addamb') == "" ]]; then
                export CAPSH_OLD=1
            else
                export CAPSH_OLD=0
            fi
        fi

        if [[ $CAPSH_NOT_FOUND == 1 || $CAPSH_OLD == 1 ]]; then
            echo "capsh is either not available in path or an old version"
            echo "EGMI needs capsh from package libcap2-bin version 1:2.22-1.2 or greater"
            echo "EGMI can attempt to download and build its own version of capsh"
            echo "(git, make and gcc are required on your system for this to succeed))"

            if [[ $FORCE == "force" ]]; then
                install_capsh
            else
                while true; do
                    read -p "Do you want to attempt this ? (y/n)" yn
                    case $yn in
                        [Yy]* ) install_capsh; break;;
                        [Nn]* ) exit;;
                        * ) echo "Please answer y or n.";;
                    esac
                done
            fi
        else
            # link system capsh to local capsh
            ln -s "$(which capsh)" $SCRIPT_DIR/capsh
        fi
    fi
fi

# Handle egmi user creation
create_egmi_user() {

    echo " - Creating user egmi (if not exist)"
    useradd egmi
    new_user_id=$(id -u egmi)
    if [[ $new_user_id == "" ]]; then
        echo "Failed to add user egmi"
        exit 43
    fi

    echo " - Creating user system folders"

    mkdir -p /home/egmi
    chown -R egmi /home/egmi

    if [ $(getent group docker) ]; then

        echo " - Adding egmi to docker group"
        usermod -a -G docker egmi
    fi
}

# Find out if user egmi exists
set +e
egmi_id=$(id -u egmi)
if [[ $egmi_id == "" ]]; then
    echo "EGMI runs under user 'egmi'"
    echo "User 'egmi' has not been found on this system"

    if [[ $FORCE == "force" ]]; then
        create_egmi_user
    else
        while true; do
            read -p "Do you want to create user egmi now ? (y/n)" yn
            case $yn in
                [Yy]* ) create_egmi_user; break;;
                [Nn]* ) exit;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi
set -e

# even if user already exists, I should ensure these folders exist or are created
mkdir -p /var/lib/egmi
chown -R egmi /var/lib/egmi

mkdir -p /var/log/egmi
chown -R egmi /var/log/egmi

if [[ ! -L $SCRIPT_DIR/../../logs || ! -d $SCRIPT_DIR/../../logs ]]; then
    rm -Rf $SCRIPT_DIR/../..//logs
    ln -s /var/log/egmi $SCRIPT_DIR/../../logs
    chown egmi $SCRIPT_DIR/../..//logs
fi


if [[ $SKIP_SYSTEM_D != "skip" ]]; then
    # Move it to SystemD units configuration folder
    mv /tmp/egmi.service $systemd_units_dir
    chmod 755 $systemd_units_dir

    # Try Service startup
    try_egmi_startup(){

        echo " - Starting EGMI"
        systemctl start egmi
    }

    if [[ $(systemctl status egmi | grep 'dead') != "" ]]; then

        if [[ $FORCE == "force" ]]; then
            try_egmi_startup
        else
            while true; do
                read -p "Do you want to try to start EGMI as SystemD service now ? (y/n)" yn
                case $yn in
                    [Yy]* ) try_egmi_startup; break;;
                    [Nn]* ) break;;
                    * ) echo "Please answer y or n.";;
                esac
            done
        fi
    fi

    # Enable Service egmi
    enable_egmi(){

        echo " - Enabling EGMI"
        systemctl enable egmi
    }

    if [[ $(systemctl status egmi | grep 'disabled;') != "" ]]; then

        if [[ $FORCE == "force" ]]; then
            enable_egmi
        else
            while true; do
                read -p "Do you want to try to Enable EGMI to start as SystemD service on machine startup ? (y/n)" yn
                case $yn in
                    [Yy]* ) enable_egmi; break;;
                    [Nn]* ) break;;
                    * ) echo "Please answer y or n.";;
                esac
            done
        fi
    fi
fi