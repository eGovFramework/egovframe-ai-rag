package com.example.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EgovWebController {

    /**
     * 채팅 페이지 제공
     */
    @GetMapping("/")
    public String chatPage() {
        return "chat";
    }
}
