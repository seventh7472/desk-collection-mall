package com.example.mall.gateway;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class AccessLogFilter implements GlobalFilter {
 private static final Logger log=LoggerFactory.getLogger(AccessLogFilter.class);
 public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
  long start=System.nanoTime();
  return chain.filter(exchange).doFinally(signal -> log.info("request method={} path={} status={} elapsedMs={}",exchange.getRequest().getMethod(),exchange.getRequest().getPath(),exchange.getResponse().getStatusCode(),(System.nanoTime()-start)/1000000));
 }
}
