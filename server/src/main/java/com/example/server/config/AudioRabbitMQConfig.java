package com.example.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 音频分析任务的 RabbitMQ 拓扑。与视频链路完全独立:自己的交换机、队列与死信兜底,
 * 音频任务不会与视频任务争抢同一个队列。
 *
 * <p>{@code RabbitTemplate}、{@code RabbitAdmin}、监听容器工厂与 JSON 转换器都由 Spring Boot
 * 自动装配(参见 {@link RabbitMQConfig}),这里只声明音频侧独有的拓扑。
 * 主队列同样绑定死信交换机:消费者第 3 次投递时自行收敛到死队列并 ACK,监听器重试真正耗尽时由 DLX 兜底。
 */
@Configuration
public class AudioRabbitMQConfig {

    @Value("${app.mq.audio-analysis.exchange:audio-analysis.exchange}")
    private String audioExchange;

    @Value("${app.mq.audio-analysis.queue:audio-analysis.queue}")
    private String audioQueue;

    @Value("${app.mq.audio-analysis.routing-key:audio.analysis}")
    private String audioRoutingKey;

    @Value("${app.mq.audio-analysis-dead.exchange:audio-analysis.dead.exchange}")
    private String deadExchange;

    @Value("${app.mq.audio-analysis-dead.queue:audio-analysis.dead.queue}")
    private String deadQueue;

    @Value("${app.mq.audio-analysis-dead.routing-key:audio.analysis.dead}")
    private String deadRoutingKey;

    @Bean
    public DirectExchange audioAnalysisExchange() {
        return new DirectExchange(audioExchange, true, false);
    }

    @Bean
    public Queue audioAnalysisQueue() {
        return QueueBuilder.durable(audioQueue)
                .deadLetterExchange(deadExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    public Binding audioAnalysisBinding() {
        return BindingBuilder.bind(audioAnalysisQueue())
                .to(audioAnalysisExchange())
                .with(audioRoutingKey);
    }

    @Bean
    public DirectExchange audioAnalysisDeadExchange() {
        return new DirectExchange(deadExchange, true, false);
    }

    @Bean
    public Queue audioAnalysisDeadQueue() {
        return QueueBuilder.durable(deadQueue).build();
    }

    @Bean
    public Binding audioAnalysisDeadBinding() {
        return BindingBuilder.bind(audioAnalysisDeadQueue())
                .to(audioAnalysisDeadExchange())
                .with(deadRoutingKey);
    }
}
