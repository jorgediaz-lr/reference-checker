#!/bin/bash

if [ -e /proc/$$/fd/255 ]; then
	scriptpath=`readlink /proc/$$/fd/255 2>/dev/null`
fi

if [ ! -n "$scriptpath" ]; then
	scriptpath="$0"
fi

PT_OPTS=
JAVA_VERSION=`echo "$(java -version 2>&1)" | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`

if [ "${JAVA_VERSION:0:3}" \< "1.8" ]; then
	PT_OPTS="-XX:MaxPermSize=512m -XX:PermSize=256m"
fi

PT_OPTS="$PT_OPTS -Xmx1024m -Xms512m -Dfile.encoding=UTF8"

cd "$(dirname "$scriptpath")"

java $PT_OPTS -cp "lib/*" com.liferay.referenceschecker.main.Launcher app-server.properties com.liferay.referenceschecker.main.ReferencesChecker main "$@"
