package com.suko.vnc.config;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StaticResourceConfig {

    // @Route(path = "/*", methods = HttpMethod.GET)
    // void staticResources(RoutingContext ctx) {
    //     StaticHandler.create("META-INF/resources")
    //         .setIndexPage("index.html")
    //         .setCachingEnabled(false)
    //         .setDirectoryListing(false)
    //         .handle(ctx);
    // }

    // @Route(path = "/novnc/*", methods = HttpMethod.GET)
    // void novncResources(RoutingContext ctx) {
    //     StaticHandler.create("novnc")
    //         .setCachingEnabled(true)
    //         .setDirectoryListing(false)
    //         .handle(ctx);
    // }

}
