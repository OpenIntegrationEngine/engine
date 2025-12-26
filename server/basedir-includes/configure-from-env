#!/usr/bin/env bash
#
# SPDX-License-Identifier: MPL-2.0
# SPDX-FileCopyrightText: 2023 NextGen Healthcare
#

set -e

APP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

custom_extension_count=`ls -1 "$APP_DIR"/custom-extensions/*.zip 2>/dev/null | wc -l`
if [ $custom_extension_count != 0 ]; then
	echo "Found ${custom_extension_count} custom extensions."
	for extension in $(ls -1 "$APP_DIR"/custom-extensions/*.zip); do
		unzip -o -q $extension -d "$APP_DIR/extensions"
	done
fi

# set storepass and keypass to 'changeme' so they aren't overwritten later
KEYSTORE_PASS=changeme
sed -i "s/^keystore\.storepass\s*=\s*.*\$/keystore.storepass = ${KEYSTORE_PASS//\//\\/}/" "$APP_DIR/conf/mirth.properties"
sed -i "s/^keystore\.keypass\s*=\s*.*\$/keystore.keypass = ${KEYSTORE_PASS//\//\\/}/" "$APP_DIR/conf/mirth.properties"

# merge the environment variables into /opt/engine/conf/mirth.properties
# db type
if ! [ -z "${DATABASE+x}" ]; then
	sed -i "s/^database\s*=\s*.*\$/database = ${DATABASE//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# db username
if ! [ -z "${DATABASE_USERNAME+x}" ]; then
	sed -i "s/^database\.username\s*=\s*.*\$/database.username = ${DATABASE_USERNAME//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# db password
if ! [ -z "${DATABASE_PASSWORD+x}" ]; then
	sed -i "s/^database\.password\s*=\s*.*\$/database.password = ${DATABASE_PASSWORD//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# db url
if ! [ -z "${DATABASE_URL+x}" ]; then
	sed -i "s/^database\.url\s*=\s*.*\$/database.url = ${DATABASE_URL//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# database max connections
if ! [ -z "${DATABASE_MAX_CONNECTIONS+x}" ]; then
	sed -i "s/^database\.max-connections\s*=\s*.*\$/database.max-connections = ${DATABASE_MAX_CONNECTIONS//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# database max retries
if ! [ -z "${DATABASE_MAX_RETRY+x}" ]; then
	sed -i "s/^database\.connection\.maxretry\s*=\s*.*\$/database.connection.maxretry = ${DATABASE_MAX_RETRY//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# database retry wait time
if ! [ -z "${DATABASE_RETRY_WAIT+x}" ]; then
	sed -i "s/^database\.connection\.retrywaitinmilliseconds\s*=\s*.*\$/database.connection.retrywaitinmilliseconds = ${DATABASE_RETRY_WAIT//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# keystore storepass
if ! [ -z "${KEYSTORE_STOREPASS+x}" ]; then
	sed -i "s/^keystore\.storepass\s*=\s*.*\$/keystore.storepass = ${KEYSTORE_STOREPASS//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# keystore keypass
if ! [ -z "${KEYSTORE_KEYPASS+x}" ]; then
	sed -i "s/^keystore\.keypass\s*=\s*.*\$/keystore.keypass = ${KEYSTORE_KEYPASS//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

if ! [ -z "${KEYSTORE_TYPE+x}" ]; then
	sed -i "s/^keystore\.type\s*=\s*.*\$/keystore.type = ${KEYSTORE_TYPE//\//\\/}/" "$APP_DIR/conf/mirth.properties"
fi

# session store
if ! [ -z "${SESSION_STORE+x}" ]; then
	LINE_COUNT=`grep "server.api.sessionstore" "$APP_DIR/conf/mirth.properties" | wc -l`
	if [ $LINE_COUNT -lt 1 ]; then
		echo -e "\nserver.api.sessionstore = ${SESSION_STORE//\//\\/}" >> "$APP_DIR/conf/mirth.properties"
	else
		sed -i "s/^server\.api\.sessionstore\s*=\s*.*\$/server.api.sessionstore = ${SESSION_STORE//\//\\/}/" "$APP_DIR/conf/mirth.properties"
	fi
fi

#server ID
if ! [ -z "${SERVER_ID+x}" ]; then
  echo -e "server.id = ${SERVER_ID//\//\\/}" > "$APP_DIR/appdata/server.id"
fi

# merge extra environment variables starting with _MP_ into mirth.properties
while read -r keyvalue; do
	KEY="${keyvalue%%=*}"
	VALUE="${keyvalue#*=}"
	VALUE=$(tr -dc '\40-\176' <<< "$VALUE")

	if ! [ -z "${KEY}" ] && ! [ -z "${VALUE}" ] && ! [[ ${VALUE} =~ ^\ +$ ]]; then

		# filter for variables starting with "_MP_"
		if [[ ${KEY} == _MP_* ]]; then

			# echo "found property ${KEY}=${VALUE}"

			# example: _MP_DATABASE_MAX__CONNECTIONS -> database.max-connections

			# remove _MP_
			# example:  DATABASE_MAX__CONNECTIONS
			ACTUAL_KEY=${KEY:4}

			# switch '__' to '-'
			# example:  DATABASE_MAX-CONNECTIONS
			ACTUAL_KEY="${ACTUAL_KEY//__/-}"

			# switch '_' to '.'
			# example:  DATABASE.MAX-CONNECTIONS
			ACTUAL_KEY="${ACTUAL_KEY//_/.}"

			# lower case
			# example:  database.max-connections
			ACTUAL_KEY="${ACTUAL_KEY,,}"

			# if key does not exist in mirth.properties append it at bottom
			LINE_COUNT=`grep "^${ACTUAL_KEY}" "$APP_DIR/conf/mirth.properties" | wc -l`
			if [ $LINE_COUNT -lt 1 ]; then
				# echo "key ${ACTUAL_KEY} not found in mirth.properties, appending. Value = ${VALUE}"
				echo -e "\n${ACTUAL_KEY} = ${VALUE//\//\\/}" >> "$APP_DIR/conf/mirth.properties"
			else # otherwise key exists, overwrite it
				# echo "key ${ACTUAL_KEY} exists, overwriting. Value = ${VALUE}"
				ESCAPED_KEY="${ACTUAL_KEY//./\\.}"
				sed -i "s/^${ESCAPED_KEY}\s*=\s*.*\$/${ACTUAL_KEY} = ${VALUE//\//\\/}/" "$APP_DIR/conf/mirth.properties"
			fi
		fi
	fi
done <<< "`printenv`"

# merge vmoptions into /opt/engine/oieserver.vmoptions
if ! [ -z "${VMOPTIONS+x}" ]; then
    PREV_IFS="$IFS"
	IFS=","
	read -ra vmoptions <<< "$VMOPTIONS"
	IFS="$PREV_IFS"

    for vmoption in "${vmoptions[@]}"
    do
        echo "${vmoption}" >> "$APP_DIR/oieserver.vmoptions"
    done
fi

# merge the user's secret mirth.properties
# takes a whole mirth.properties file and merges line by line with /opt/engine/conf/mirth.properties
if [ -f /run/secrets/mirth_properties ]; then

    # add new line in case /opt/engine/conf/mirth.properties doesn't end with one
    echo "" >> "$APP_DIR/conf/mirth.properties"

    while read -r keyvalue; do
        KEY="${keyvalue%%=*}"
        VALUE="${keyvalue#*=}"

        # remove leading and trailing white space
        KEY="$(echo -e "${KEY}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
        VALUE="$(echo -e "${VALUE}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

        if ! [ -z "${KEY}" ] && ! [ -z "${VALUE}" ] && ! [[ ${VALUE} =~ ^\ +$ ]]; then
            # if key does not exist in mirth.properties append it at bottom
            LINE_COUNT=`grep "^${KEY}" "$APP_DIR/conf/mirth.properties" | wc -l`
            if [ $LINE_COUNT -lt 1 ]; then
                # echo "key ${KEY} not found in mirth.properties, appending. Value = ${VALUE}"
                echo -e "${KEY} = ${VALUE//\//\\/}" >> "$APP_DIR/conf/mirth.properties"
            else # otherwise key exists, overwrite it
                # echo "key ${KEY} exists, overwriting. Value = ${VALUE}"
                ESCAPED_KEY="${KEY//./\\.}"
                sed -i "s/^${ESCAPED_KEY}\s*=\s*.*\$/${KEY} = ${VALUE//\//\\/}/" "$APP_DIR/conf/mirth.properties"
            fi
        fi
    done <<< "`cat /run/secrets/mirth_properties`"
fi

# merge the user's secret vmoptions
# takes a whole oieserver.vmoptions file and merges line by line with /opt/engine/oieserver.vmoptions
if [ -f /run/secrets/oieserver_vmoptions ]; then
    (cat /run/secrets/oieserver_vmoptions ; echo "") >> "$APP_DIR/oieserver.vmoptions"
fi

# download jars from this url "$CUSTOM_JARS_DOWNLOAD", set by user
if ! [ -z "${CUSTOM_JARS_DOWNLOAD+x}" ]; then
	echo "Downloading Jars at ${CUSTOM_JARS_DOWNLOAD}"
	if ! [ -z "${ALLOW_INSECURE}" ] && [ "${ALLOW_INSECURE}" == "true" ]; then
		curl -ksSLf "${CUSTOM_JARS_DOWNLOAD}" -o userJars.zip || echo "problem with custom jars download"
	else
		curl -sSLf "${CUSTOM_JARS_DOWNLOAD}" -o userJars.zip || echo "problem with custom jars download"
	fi

	# Unzipping contents of userJars.zip into /opt/engine/server-launcher-lib folder
	if [ -e "userJars.zip" ]; then
		echo "Unzipping contents of userJars.zip into $APP_DIR/server-launcher-lib"
		unzip userJars.zip -d "$APP_DIR/server-launcher-lib"
		# removing the downloaded zip file
		rm userJars.zip
	fi
fi

# download extensions from this url "$EXTENSIONS_DOWNLOAD", set by user
if ! [ -z "${EXTENSIONS_DOWNLOAD+x}" ]; then
	echo "Downloading extensions at ${EXTENSIONS_DOWNLOAD}"
	if ! [ -z "${ALLOW_INSECURE}" ] && [ "${ALLOW_INSECURE}" == "true" ]; then
		curl -ksSLf "${EXTENSIONS_DOWNLOAD}" -o  userExtensions.zip || echo "problem with extensions download"
	else
		curl -sSLf "${EXTENSIONS_DOWNLOAD}" -o userExtensions.zip || echo "problem with extensions download"
	fi

	# Unzipping contents of userExtensions.zip
	if [ -e "userExtensions.zip" ]; then
		echo "Unzipping contents of userExtensions.zip"
		mkdir /tmp/userextensions
		unzip userExtensions.zip -d /tmp/userextensions
		# removing the downloaded zip file
		rm userExtensions.zip

		# Unzipping contents of individual extension zip files into /opt/engine/extensions folder
		zipFileCount=`ls -1 /tmp/userextensions/*.zip 2>/dev/null | wc -l`
		if [ $zipFileCount != 0 ]; then
			echo "Unzipping contents of /tmp/userextensions/ zips into $APP_DIR/extensions"
			for f in /tmp/userextensions/*.zip; do unzip "$f" -d "$APP_DIR/extensions"; done
		fi
		# removing the tmp folder
		rm -rf /tmp/userextensions
	fi
fi

# download keystore
if ! [ -z "${KEYSTORE_DOWNLOAD+x}" ]; then
	echo "Downloading keystore at ${KEYSTORE_DOWNLOAD}"
	if ! [ -z "${ALLOW_INSECURE}" ] && [ "${ALLOW_INSECURE}" == "true" ]; then
		curl -ksSLf "${KEYSTORE_DOWNLOAD}" -o "$APP_DIR/appdata/keystore.jks" || echo "problem with keystore download"
	else
		curl -sSLf "${KEYSTORE_DOWNLOAD}" -o "$APP_DIR/appdata/keystore.jks" || echo "problem with keystore download"
	fi
fi

# if delay is set as an environment variable then wait that long in seconds
if ! [ -z "${DELAY+x}" ]; then
	sleep $DELAY
fi

# if there are any arguments, invoke them as a command
if [ $# -ne 0 ]; then
	exec "$@"
fi
