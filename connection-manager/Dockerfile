FROM golang:1.24 AS cpp-builder

RUN apt-get update && apt-get install -y cmake g++

WORKDIR /app

COPY ot-engine/ ./ot-engine/

RUN cmake -S ot-engine -B ot-engine/build
RUN cmake --build ot-engine/build --target ot-engine-lib

FROM golang:1.24 AS builder

RUN apt-get update && apt-get install -y gcc g++

WORKDIR /app

COPY --from=cpp-builder /app/ot-engine ./ot-engine

COPY connection-manager/go.mod connection-manager/go.sum ./connection-manager/

RUN cd connection-manager && go mod download

COPY connection-manager/ ./connection-manager/

RUN cd connection-manager && CGO_ENABLED=1 go build -o ../binary

FROM debian:bookworm-slim

RUN useradd -u 1000 -m appuser

USER appuser

WORKDIR /app

COPY --from=builder /app/binary ./binary
COPY --from=builder /app/ot-engine/build/libot-engine-lib.so ./ot-engine/build/libot-engine-lib.so
ENV LD_LIBRARY_PATH=/app/ot-engine/build
EXPOSE 8080

CMD [ "./binary" ]
