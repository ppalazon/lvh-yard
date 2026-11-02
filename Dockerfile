# The dashboard image: docker build -t lvh-yard-dashboard .
# The first two stages run on the building computer's own platform, once, whatever platforms the image is built for:
# app.css and the jar are the same on every CPU. Only the last stage is per platform.

# app.css, from the classes the web views use (mise run css).
FROM --platform=$BUILDPLATFORM node:24 AS css
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --silent
COPY dashboard/src dashboard/src
RUN npx tailwindcss -i dashboard/src/main/css/app.css -o app.css --minify

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
# The release workflow passes the version from its tag; a local build keeps the poms' own.
ARG VERSION
COPY . .
COPY --from=css /src/app.css dashboard/src/main/resources/static/app.css
# The tests need Docker and run in `mise run check`, before a release is tagged.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -pl dashboard package -DskipTests ${VERSION:+-Drevision=$VERSION} \
    && cp dashboard/target/dashboard-*.jar /dashboard.jar

FROM eclipse-temurin:25-jre
# Root, so the dashboard can read the Docker socket mounted into it, whichever group owns the socket on the host.
COPY --from=build /dashboard.jar /app/dashboard.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/dashboard.jar"]
