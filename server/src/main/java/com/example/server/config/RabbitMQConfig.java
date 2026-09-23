package com.example.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 视频分析任务的 RabbitMQ 拓扑。RabbitTemplate、RabbitAdmin 与 @RabbitListener 的容器工厂
 * 都由 Spring Boot 自动装配，这里只声明交换机、队列、绑定与 JSON 转换器。
 *
 * <p>主队列绑定死信交换机做兜底：正常路径下消费者会在第 3 次投递时自行收敛到死队列并 ACK，
 * 只有监听器重试真正耗尽（例如 Redis 递增失败导致投递计数停在 0）时才会由 DLX 兜底转投。
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.mq.video-analysis.exchange:video-analysis.exchange}")
    private String analysisExchange;

    @Value("${app.mq.video-analysis.queue:video-analysis.queue}")
    private String analysisQueue;

    @Value("${app.mq.video-analysis.routing-key:video.analysis}")
    private String analysisRoutingKey;

    @Value("${app.mq.video-analysis-dead.exchange:video-analysis.dead.exchange}")
    private String deadExchange;

    @Value("${app.mq.video-analysis-dead.queue:video-analysis.dead.queue}")
    private String deadQueue;

    @Value("${app.mq.video-analysis-dead.routing-key:video.analysis.dead}")
    private String deadRoutingKey;

    @Bean
    public DirectExchange videoAnalysisExchange() {
        return new DirectExchange(analysisExchange, true, false);
    }

    @Bean
    public Queue videoAnalysisQueue() {
        return QueueBuilder.durable(analysisQueue)
                .deadLetterExchange(deadExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    public Binding videoAnalysisBinding() {
        return BindingBuilder.bind(videoAnalysisQueue())
                .to(videoAnalysisExchange())
                .with(analysisRoutingKey);
    }

    @Bean
    public DirectExchange videoAnalysisDeadExchange() {
        return new DirectExchange(deadExchange, true, false);
    }

    @Bean
    public Queue videoAnalysisDeadQueue() {
        return QueueBuilder.durable(deadQueue).build();
    }

    @Bean
    public Binding videoAnalysisDeadBinding() {
        return BindingBuilder.bind(videoAnalysisDeadQueue())
                .to(videoAnalysisDeadExchange())
                .with(deadRoutingKey);
    }

    /** 唯一的 MessageConverter 会被 Spring Boot 同时用于 RabbitTemplate 与监听容器工厂。 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
