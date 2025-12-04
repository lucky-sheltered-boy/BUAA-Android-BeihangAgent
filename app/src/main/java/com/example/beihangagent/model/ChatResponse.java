package com.example.beihangagent.model;

import java.util.List;

public class ChatResponse {
    public List<Choice> choices;

    public static class Choice {
        public Message message;
    }

    public static class Message {
        public String role;
        public String content;
    }
}
