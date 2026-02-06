package com.redhat.cloud.notifications;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class MockServerLifecycleManager {

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;

    public static void start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        mockServerUrl = "http://localhost:" + wireMockServer.port();
        System.out.println("WireMock started on: " + mockServerUrl);
    }

    public static String getMockServerUrl() {
        return mockServerUrl;
    }

    public static WireMockServer getClient() {
        return wireMockServer;
    }

    public static void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
