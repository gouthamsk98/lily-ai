FROM rust:1.89 AS builder
WORKDIR /app
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo "fn main() {}" > src/main.rs && cargo build --release && rm -rf src
COPY . .
ENV SQLX_OFFLINE=true
RUN touch src/main.rs && cargo build --release

FROM debian:trixie-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/budget_tracker_backend /usr/local/bin/
COPY --from=builder /app/migrations /app/migrations
WORKDIR /app
EXPOSE 8080
CMD ["budget_tracker_backend"]
