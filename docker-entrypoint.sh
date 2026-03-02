#!/bin/bash

jvm_args=()
extra_args=()

if [[ -n "${LOG_LEVEL}" ]]; then
    # The SLF4J backend is slf4j-simple, configured through system properties.
    # The Gradle-generated start script only takes JVM options through
    # JAVA_OPTS: script arguments are passed to the program, not the JVM.
    jvm_args+=("-Dorg.slf4j.simpleLogger.log.ru.herobrine1st=${LOG_LEVEL}")
fi

if [[ -n "${CONFIG_PATH}" ]]; then
    extra_args+=("-config=${CONFIG_PATH}")
fi

JAVA_OPTS="${jvm_args[*]}" exec ./server/bin/server -config=/app/application.conf "${extra_args[@]}" "${@}"
