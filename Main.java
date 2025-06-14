package org.example;

import org.apache.commons.io.input.TailerListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;




public class Main {
    private static TailerListener ApiContextInitializer;

    public static void main(String[] args) throws TelegramApiException {



        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {

            botsApi.registerBot(new s());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
