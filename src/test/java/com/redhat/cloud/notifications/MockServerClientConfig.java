package com.redhat.cloud.notifications;

import org.mockserver.client.MockServerClient;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerClientConfig {

    private final MockServerClient mockServerClient;

    public MockServerClientConfig(String containerIpAddress, Integer serverPort) {
        mockServerClient = new MockServerClient(containerIpAddress, serverPort);
    }

    public void addMock() {
        this.mockServerClient
                .when(request()
                        .withPath("/internal/validation/baet")
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(""));
    }

    public void addMock2() {
        this.mockServerClient
                .when(request()
                                .withPath("/internal/validation/baet")
                )
                .respond(response()
                        .withStatusCode(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("blabla"));
    }
}
