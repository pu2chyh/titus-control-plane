package com.netflix.titus.gateway.eviction;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.titus.api.eviction.service.EvictionException;
import com.netflix.titus.grpc.protogen.EvictionQuota;
import com.netflix.titus.grpc.protogen.EvictionServiceEvent;
import com.netflix.titus.grpc.protogen.EvictionServiceGrpc;
import com.netflix.titus.grpc.protogen.ObserverEventRequest;
import com.netflix.titus.grpc.protogen.Reference;
import com.netflix.titus.grpc.protogen.SystemDisruptionBudget;
import com.netflix.titus.grpc.protogen.TaskTerminateRequest;
import com.netflix.titus.grpc.protogen.TaskTerminateResponse;
import com.netflix.titus.runtime.connector.eviction.EvictionServiceClient;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;

import static com.netflix.titus.runtime.endpoint.common.grpc.GrpcUtil.attachCancellingCallback;
import static com.netflix.titus.runtime.endpoint.common.grpc.GrpcUtil.safeOnError;
import static com.netflix.titus.runtime.eviction.endpoint.grpc.GrpcEvictionModelConverters.toCoreReference;
import static com.netflix.titus.runtime.eviction.endpoint.grpc.GrpcEvictionModelConverters.toGrpcEvent;
import static com.netflix.titus.runtime.eviction.endpoint.grpc.GrpcEvictionModelConverters.toGrpcEvictionQuota;
import static com.netflix.titus.runtime.eviction.endpoint.grpc.GrpcEvictionModelConverters.toGrpcSystemDisruptionBudget;

@Singleton
public class GatewayGrpcEvictionService extends EvictionServiceGrpc.EvictionServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(GatewayGrpcEvictionService.class);

    private final EvictionServiceClient evictionServiceClient;

    @Inject
    public GatewayGrpcEvictionService(EvictionServiceClient evictionServiceClient) {
        this.evictionServiceClient = evictionServiceClient;
    }

    @Override
    public void getDisruptionBudget(Reference request, StreamObserver<SystemDisruptionBudget> responseObserver) {
        Subscription subscription = evictionServiceClient.getDisruptionBudget(toCoreReference(request)).subscribe(
                next -> responseObserver.onNext(toGrpcSystemDisruptionBudget(next)),
                e -> safeOnError(logger, e, responseObserver),
                responseObserver::onCompleted
        );
        attachCancellingCallback(responseObserver, subscription);
    }

    @Override
    public void getEvictionQuota(Reference request, StreamObserver<EvictionQuota> responseObserver) {
        Subscription subscription = evictionServiceClient.getEvictionQuota(toCoreReference(request)).subscribe(
                next -> responseObserver.onNext(toGrpcEvictionQuota(next)),
                e -> safeOnError(logger, e, responseObserver),
                responseObserver::onCompleted
        );
        attachCancellingCallback(responseObserver, subscription);
    }

    @Override
    public void terminateTask(TaskTerminateRequest request, StreamObserver<TaskTerminateResponse> responseObserver) {
        Subscription subscription = evictionServiceClient.terminateTask(request.getTaskId(), request.getReason())
                .subscribe(
                        () -> responseObserver.onNext(TaskTerminateResponse.newBuilder().setAllowed(true).build()),
                        e -> {
                            if (e instanceof EvictionException) {
                                // TODO Improve error reporting
                                responseObserver.onNext(TaskTerminateResponse.newBuilder()
                                        .setAllowed(true)
                                        .setReasonCode("failure")
                                        .setReasonMessage(e.getMessage())
                                        .build()
                                );
                            } else {
                                safeOnError(logger, e, responseObserver);
                            }
                        }
                );
        attachCancellingCallback(responseObserver, subscription);
    }

    @Override
    public void observeEvents(ObserverEventRequest request, StreamObserver<EvictionServiceEvent> responseObserver) {
        Subscription subscription = evictionServiceClient.observeEvents(request.getIncludeSnapshot()).subscribe(
                event -> responseObserver.onNext(toGrpcEvent(event)),
                e -> safeOnError(logger, e, responseObserver),
                responseObserver::onCompleted
        );
        attachCancellingCallback(responseObserver, subscription);
    }
}
