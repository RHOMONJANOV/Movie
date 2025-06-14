package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class s extends TelegramLongPollingBot {
    private final Map<Integer, Message> movieMessages = new HashMap<>();
    private final Map<Integer, Integer> movieViewCounts = new HashMap<>();
    private final String channelId = "@TopHumansM";
    private final List<Long> admins = new ArrayList<>();
    private static final String BOT_TOKEN = "8017392875:AAEY866eja-bd8pIvNWAy0TszUOMSDuJfXE";
    private static final String DATA_FILE = "movie_data.ser"; // Ma'lumotlarni saqlash uchun fayl

    public s() {
        admins.add(7106426031L); // Admin chat ID
        loadData(); // Bot ishga tushganda ma'lumotlarni yuklash
    }

    // Ma'lumotlarni fayldan yuklash
    private void loadData() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            Map<Integer, Message> loadedMessages = (Map<Integer, Message>) ois.readObject();
            Map<Integer, Integer> loadedViewCounts = (Map<Integer, Integer>) ois.readObject();
            movieMessages.putAll(loadedMessages);
            movieViewCounts.putAll(loadedViewCounts);
            System.out.println("Ma'lumotlar muvaffaqiyatli yuklandi: " + movieMessages.size() + " ta kino");
        } catch (FileNotFoundException e) {
            System.out.println("Ma'lumotlar fayli topilmadi, yangi fayl yaratiladi.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Ma'lumotlarni yuklashda xato: " + e.getMessage());
        }
    }

    // Ma'lumotlarni faylga saqlash
    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(movieMessages);
            oos.writeObject(movieViewCounts);
            System.out.println("Ma'lumotlar muvaffaqiyatli saqlandi.");
        } catch (IOException e) {
            System.err.println("Ma'lumotlarni saqlashda xato: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasChannelPost()) {
                handleChannelPost(update.getChannelPost());
            } else if (update.hasMessage()) {
                handleUserMessage(update.getMessage());
            }
        } catch (Exception e) {
            notifyAdmins("Xatolik yuz berdi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleChannelPost(Message channelPost) {
        if (channelPost.hasVideo() || channelPost.hasDocument()) {
            String text = channelPost.getCaption();
            if (text != null) {
                Pattern pattern = Pattern.compile("(?i)kod[:\\s]+(\\d+)");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    int movieNumber = Integer.parseInt(matcher.group(1));
                    movieMessages.put(movieNumber, channelPost);
                    movieViewCounts.putIfAbsent(movieNumber, 0);
                    saveData(); // Yangi kino qo'shilganda ma'lumotlarni saqlash
                    System.out.println("Yangi kino qo‘shildi: " + movieNumber);
                }
            }
        }
    }

    private void handleUserMessage(Message userMessage) {
        long chatId = userMessage.getChatId();
        String text = userMessage.getText();

        try {
            if (text.equals("/start")) {
                checkSubscriptionAndSendWelcome(chatId);
            } else if (text.equals("/admin") && admins.contains(chatId)) {
                sendAdminMenu(chatId);
            } else if (text.matches("\\d+")) {
                int movieNumber = Integer.parseInt(text);
                if (isUserSubscribed(chatId)) {
                    sendMovieToUser(chatId, movieNumber);
                } else {
                    sendSubscriptionRequiredMessage(chatId);
                }
            } else {
                sendMessage(chatId, "Iltimos, kinoning kod raqamini yuboring (masalan: 23)");
            }
        } catch (TelegramApiException e) {
            notifyAdmins("Foydalanuvchi xabarini qayta ishlashda xato: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isUserSubscribed(long chatId) throws TelegramApiException {
        GetChatMember getChatMember = new GetChatMember();
        getChatMember.setChatId(channelId);
        getChatMember.setUserId(chatId);

        try {
            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();
            return status.equals("member") || status.equals("administrator") || status.equals("creator");
        } catch (TelegramApiException e) {
            notifyAdmins("A'zolikni tekshirishda xato: " + e.getMessage());
            return false;
        }
    }

    private void sendMovieToUser(long chatId, int movieNumber) throws TelegramApiException {
        if (movieMessages.containsKey(movieNumber)) {
            Message movieMessage = movieMessages.get(movieNumber);
            ForwardMessage forward = new ForwardMessage();
            forward.setChatId(String.valueOf(chatId));
            forward.setFromChatId(channelId);
            forward.setMessageId(movieMessage.getMessageId());

            execute(forward);
            // Ko'rishlar sonini oshirish
            movieViewCounts.merge(movieNumber, 1, Integer::sum);
            saveData(); // Ko'rishlar soni o'zgarganda saqlash
        } else {
            sendMessage(chatId, "Kechirasiz, " + movieNumber + "-kodli kino topilmadi.");
        }
    }

    private void checkSubscriptionAndSendWelcome(long chatId) throws TelegramApiException {
        if (isUserSubscribed(chatId)) {
            sendWelcomeMessage(chatId);
        } else {
            sendSubscriptionRequiredMessage(chatId);
        }
    }

    private void sendWelcomeMessage(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("🎬 Kino botga xush kelibsiz!\n\n" +
                "Kino olish uchun uning kodini yuboring (masalan: 23)\n" +
                "Kanalimiz: " + channelId);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        if (!movieMessages.isEmpty()) {
            List<Integer> movieNumbers = new ArrayList<>(movieMessages.keySet());
            Collections.sort(movieNumbers);

            KeyboardRow row = new KeyboardRow();
            for (int i = 0; i < Math.min(6, movieNumbers.size()); i++) {
                row.add(new KeyboardButton(String.valueOf(movieNumbers.get(i))));
                if (row.size() >= 3) {
                    keyboard.add(row);
                    row = new KeyboardRow();
                }
            }
            if (!row.isEmpty()) {
                keyboard.add(row);
            }
        }

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        execute(message);
    }

    private void sendSubscriptionRequiredMessage(long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Kinolarni ko'rish uchun avval kanalimizga a'zo bo'ling: " + channelId + "\n" +
                "Keyin /start ni qayta yuboring.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("A'zo bo'ldim ✅"));
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        execute(message);
    }

    private void sendAdminMenu(long chatId) throws TelegramApiException {
        StringBuilder stats = new StringBuilder("📊 Eng ko‘p ko‘rilgan kinolar (Top 5):\n\n");
        movieViewCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    int movieNumber = entry.getKey();
                    int viewCount = entry.getValue();
                    String caption = movieMessages.get(movieNumber).getCaption();
                    stats.append("Kod: ").append(movieNumber)
                            .append(" | Ko‘rishlar: ").append(viewCount)
                            .append(" | ").append(caption != null ? caption.substring(0, Math.min(caption.length(), 50)) : "Nomsiz")
                            .append("\n");
                });

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Admin menyusi:\n" +
                "Kanalga kino qo‘shganda sarlavhaga 'Kod: 23' kabi raqam yozing.\n\n" +
                stats.toString());

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Statistika 📊"));
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        message.setReplyMarkup(keyboardMarkup);

        execute(message);
    }

    private void notifyAdmins(String errorMessage) {
        for (Long adminId : admins) {
            try {
                sendMessage(adminId, "⚠️ Xatolik: " + errorMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        execute(message);
    }

    @Override
    public String getBotUsername() {
        return "@rezerMovie_bot";
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }
}