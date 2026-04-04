package com.doceditor.docmanager.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class GrpcServerConfig {

    private Server server;
    private final DocumentRecoveryService documentRecoveryService;

    public GrpcServerConfig(DocumentRecoveryService documentRecoveryService) {
        this.documentRecoveryService = documentRecoveryService;
    }

    @PostConstruct
    public void start() throws Exception {
        server = ServerBuilder
                .forPort(9090)
                .addService(documentRecoveryService)
                .build()
                .start();
        System.out.println("gRPC server started on port 9090");
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            System.out.println("gRPC server shut down");
        }
    }
}
