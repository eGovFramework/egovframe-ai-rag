package com.example.chat.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 기술 정보 JSON 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"name", "category", "description", "features", "useCases"})
public class TechnologyResponse {
    private String name;
    private String category;
    private String description;
    private List<String> features;
    private List<String> useCases;
} 