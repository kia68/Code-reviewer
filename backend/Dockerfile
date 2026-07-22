FROM eclipse-temurin:21-jre
WORKDIR /app

# Trust extra CA certs (e.g. a corporate TLS-intercepting proxy) so outbound
# HTTPS from the app (Anthropic API) succeeds. Drop *.crt files into
# backend/certs/. No-op when the folder only holds .keep.
COPY certs/ /tmp/certs/
RUN for c in /tmp/certs/*.crt; do \
      [ -e "$c" ] || continue; \
      keytool -importcert -noprompt -trustcacerts \
        -alias "proxy-$(basename "$c" .crt)" \
        -file "$c" \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit; \
    done

COPY build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
