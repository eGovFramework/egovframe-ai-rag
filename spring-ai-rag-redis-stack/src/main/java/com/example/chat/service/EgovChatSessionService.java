package com.example.chat.service;

import com.example.chat.dto.ChatSession;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 채팅 세션 관리 서비스 인터페이스
 * 세션 생성, 조회, 삭제 및 메시지 관리 기능 제공
 */
public interface EgovChatSessionService {
    
    /**
     * 새로운 채팅 세션을 생성
     * 
     * @return 생성된 채팅 세션
     */
    ChatSession createNewSession();
    
    /**
     * 모든 채팅 세션 목록을 가져옴
     * 
     * @return 채팅 세션 목록 (최신순 정렬)
     */
    List<ChatSession> getAllSessions();
    
    /**
     * 특정 세션의 메시지 목록을 가져옴
     * 
     * @param sessionId 세션 ID
     * @return 메시지 목록
     */
    List<Message> getSessionMessages(String sessionId);
    
    /**
     * 세션의 제목을 업데이트
     * 
     * @param sessionId 세션 ID
     * @param title 새로운 제목
     */
    void updateSessionTitle(String sessionId, String title);
    
    /**
     * 세션의 마지막 메시지 시간을 업데이트
     * 
     * @param sessionId 세션 ID
     */
    void updateLastMessageTime(String sessionId);
    
    /**
     * 첫 메시지를 기반으로 세션 제목을 생성
     * 
     * @param firstMessage 첫 메시지
     * @return 생성된 제목
     */
    String generateSessionTitle(String firstMessage);
    
    /**
     * 세션이 존재하는지 확인
     * 
     * @param sessionId 세션 ID
     * @return 존재 여부
     */
    boolean sessionExists(String sessionId);
    
    /**
     * 세션을 삭제
     * 
     * @param sessionId 세션 ID
     */
    void deleteSession(String sessionId);
}