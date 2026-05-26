FROM golang:1.24-alpine AS builder
WORKDIR /app

# Use multiple GOPROXY fallbacks so the build works on VPS environments
# that may not reach proxy.golang.org directly.
ENV GOPROXY=https://proxy.golang.org,https://goproxy.io,https://goproxy.cn,direct
ENV GONOSUMDB=*

COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o scheduler .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
COPY --from=builder /app/scheduler .
EXPOSE 8082
CMD ["./scheduler"]
