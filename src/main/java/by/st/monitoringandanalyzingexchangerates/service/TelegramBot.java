package by.st.monitoringandanalyzingexchangerates.service;

import by.st.monitoringandanalyzingexchangerates.config.BotConfig;
import by.st.monitoringandanalyzingexchangerates.response.BankResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Builder
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final BankApiService bankApiService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId;
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            chatId = update.getMessage().getChatId();
            if (messageText.equals("/start")) {
                startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                showBankOptions(chatId);
            } else {
                sendHelpMessage(chatId);
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            chatId = update.getMessage().getChatId();
            if (callbackData.startsWith("bank:")) {
                String selectedBank = callbackData.substring(5);
                handleBankSelection(chatId, selectedBank);
            }
        }
    }

    private void startCommandReceived(long chatId, String firstName) {
        String answer = "Привет  " + firstName + ", рады видеть вас.";
        sendMessage(chatId, answer);
    }

    private void showBankOptions(long chatId) {
        List<String> banks = bankApiService.getBank();
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (String bank : banks) {
            buttons.add(InlineKeyboardButton.builder()
                    .text(bank)
                    .callbackData("bank:" + bank)
                    .build());
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(buttons));
        SendMessage message = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("Выберите банк:")
                .replyMarkup(markup)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Не удалось отправить сообщение: " + e.getMessage());
        }
    }

    private void sendHelpMessage(long chatId) {
        String helpMessage = "Извините, я не понимаю эту команду. Вы можете использовать команду /start, чтобы начать.";
        sendMessage(chatId, helpMessage);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Не удалось отправить сообщение: " + e.getMessage());
        }
    }

    private void handleBankSelection(long chatId, String selectedBank) {
        String apiUrl = "https://api.nbrb.by/exrates/rates/" + selectedBank;
        ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
        String rate = parseRateFromResponse(response.getBody());
        sendMessage(chatId, "Текущий курс валюты в " + selectedBank + " составляет: " + rate);
    }

    private String parseRateFromResponse(String responseBody) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            BankResponse response = mapper.readValue(responseBody, BankResponse.class);
            return String.valueOf(response.getCur_OfficialRate());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
