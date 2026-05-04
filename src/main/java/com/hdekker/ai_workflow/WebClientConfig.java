package com.hdekker.ai_workflow;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {
	
	// This bean will customize the WebClient.Builder used by the OpenAiChatModel
    @Bean
    public WebClient.Builder openAiWebClientBuilderCustomizer() {
        // 1. Create a Reactor Netty HttpClient instance
        HttpClient httpClient = HttpClient.create()
                // 2. Configure the response timeout (for the entire transaction)
                .responseTimeout(Duration.ofSeconds(300)) // 5 minutes (adjust as needed)
                
                // 3. Configure the connection timeout
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000); // 60 seconds

        // 4. Return the WebClient.Builder configured with the custom HttpClient
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

}
