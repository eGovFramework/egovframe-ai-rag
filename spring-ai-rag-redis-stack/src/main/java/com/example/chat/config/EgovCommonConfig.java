package com.example.chat.config;

import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * egovframe 공통 설정
 */
@Configuration
public class EgovCommonConfig {

    @Bean(name = "leaveaTrace")
    public LeaveaTrace leaveaTrace() {
        return new LeaveaTrace();
    }
}
