package com.agentplatform.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSenderImpl javaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }
}