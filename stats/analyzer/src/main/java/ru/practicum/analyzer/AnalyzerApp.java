package ru.practicum.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.practicum.analyzer.processor.EventSimilarityProcessor;
import ru.practicum.analyzer.processor.UserActionProcessor;

@SpringBootApplication
public class AnalyzerApp {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AnalyzerApp.class, args);

        final EventSimilarityProcessor eventSimilarityProcessor = context.getBean(EventSimilarityProcessor.class);
        final UserActionProcessor userActionProcessor = context.getBean(UserActionProcessor.class);

        Thread eventSimilarityThread = new Thread(eventSimilarityProcessor);
        eventSimilarityThread.setName("EventSimilarityHandlerThread");
        eventSimilarityThread.start();

        Thread userActionThread = new Thread(userActionProcessor);
        userActionThread.setName("UserActionHandlerThread");
        userActionThread.start();
    }
}
